package com.ofit.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the machine.
 *
 * The session owns the protocol conversation: it discovers the machine, unlocks it, arms it,
 * polls it, and applies control commands. It knows nothing about Bluetooth beyond the
 * [Transport] interface, which is what lets it be tested without a phone.
 *
 * Two modes, and the difference matters:
 *  - **read only**: connect and poll. The machine stays in local-control mode, so its physical
 *    buttons keep working normally while the user watches.
 *  - **control**: run the discovery and unlock sequence, then arm the machine. From then on the
 *    machine's START button is reported to the app instead of starting its own workout.
 *
 * Evidence: research.md section 8.
 */
class Session(
    private val transport: Transport,
    private val holder: TreadmillStateHolder,
    /** How the machine gets unlocked; replaced when the user saves an unlock code. */
    @Volatile var unlockProvider: UnlockProvider,
    private val limitsProvider: () -> SafetyLimits,
    private val log: SessionLog = SessionLog(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val requestMutex = Mutex()
    @Volatile private var pending: PendingRequest? = null

    /** Events that are not state changes, such as a completed handshake. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** Session level events. */
    val events: SharedFlow<String> = _events

    private var notificationJob: Job? = null

    /** The session log. */
    val sessionLog: SessionLog get() = log

    /** Start collecting notifications from the transport. */
    fun start() {
        if (notificationJob != null) return
        notificationJob = scope.launch {
            transport.notifications.collect { bytes ->
                log.inbound(bytes)
                onNotification(bytes)
            }
        }
        scope.launch {
            transport.connectionState.collect { state ->
                holder.update { it.copy(connectionState = state) }
                if (state == ConnectionState.DISCONNECTED) {
                    // A dropped link is a stop condition: never report the machine as running.
                    holder.update {
                        it.copy(
                            readiness = MachineReadiness.UNKNOWN,
                            lastError = "The connection to the treadmill was lost.",
                        )
                    }
                    pending?.deferred?.completeExceptionally(IllegalStateException("link lost"))
                    pending = null
                }
            }
        }
    }

    /** Release the session. */
    fun close() {
        notificationJob?.cancel()
        scope.cancel()
    }

    // ---------------------------------------------------------------- connect

    /** Connect to the machine. */
    suspend fun connect() {
        holder.update { it.copy(connectionState = ConnectionState.CONNECTING) }
        transport.connect()
        holder.update {
            it.copy(
                connectionState = ConnectionState.CONNECTED,
                readiness = MachineReadiness.READ_ONLY,
            )
        }
        log.event("connected")
        _events.tryEmit("Connected")
    }

    /** Disconnect from the machine. */
    suspend fun disconnect() {
        gate.onAppStop()
        holder.update { it.copy(connectionState = ConnectionState.DISCONNECTING) }
        transport.disconnect()
        holder.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
        log.event("disconnected")
    }

    // ------------------------------------------------------------- discovery

    /**
     * Ask the machine about itself.
     *
     * The order matters: the serial number, part number, and model come from these queries and
     * are the inputs to the unlock.
     */
    suspend fun discover(): DeviceInfo {
        log.event("discovery")

        // The vendor application's handshake, byte for byte, from an HCI capture of it talking to
        // the user's own I_TL console (2026-11-23). The console answers reads only after it.
        val replies = mutableMapOf<Int, ByteArray>()
        for (step in Handshake.STEPS) {
            val frame = request(step.command, step.content, device = step.device)
            // The whole frame, for the unlock computation.
            replies[step.command] = Command.encode(frame.device, frame.command, frame.content)
        }
        computedUnlock = runCatching {
            UnlockCalculator.compute(
                deviceInfo = replies.getValue(CommandId.DEVICE_INFO),
                systemInfo = replies.getValue(CommandId.SYSTEM_INFO),
                versionInfo = replies.getValue(CommandId.VERSION_INFO),
            )
        }.getOrNull()
        log.event("unlock computed from the handshake: ${computedUnlock != null}")
        // The machine's own speed and incline range, read now (a plain read, no unlock needed) so
        // the app knows it from the moment it connects. Not fatal: the user's ceilings still apply.
        runCatching { readStartupFields() }
            .onSuccess {
                val st = holder.state.value
                log.event("machine range: speed ${st.minSpeedKph}..${st.maxSpeedKph} km/h, incline ${st.minInclinePercent}..${st.maxInclinePercent} %")
            }
            .onFailure { log.event("machine range read failed: ${it.message}") }
        // 0x95 answers with the machine's serial as text: status, length, then ASCII.
        val serialText = runCatching {
            val frame = request(CommandId.SERIAL_NUMBER, ByteArray(0), device = EquipmentId.GENERAL)
            val c = frame.content
            if (c.size >= 2) String(c.copyOfRange(2, minOf(c.size, 2 + (c[1].toInt() and 0xFF))), Charsets.US_ASCII) else null
        }.getOrNull()

        val deviceInfo = DeviceInfo()
        // The handshake was answered, so the machine is talking: watching only until unlocked.
        holder.update {
            it.copy(
                deviceInfo = deviceInfo,
                connectionState = ConnectionState.CONNECTED,
                readiness = if (it.readiness == MachineReadiness.UNKNOWN) MachineReadiness.READ_ONLY else it.readiness,
            )
        }
        log.event("discovery complete: serial=$serialText")
        return deviceInfo
    }

    /**
     * The unlock worked out from this console's own handshake answers (UnlockCalculator), so no
     * captured code is needed. Null until a handshake has been answered.
     */
    @Volatile var computedUnlock: ByteArray? = null
        private set

    /** The vendor application's handshake, in its order. */
    object Handshake {
        class Step(val device: Int, val command: Int, val content: ByteArray)

        val STEPS: List<Step> = listOf(
            Step(EquipmentId.GENERAL, 0x81, ByteArray(0)),
            Step(EquipmentId.TREADMILL, 0x80, ByteArray(0)),
            Step(EquipmentId.TREADMILL, 0x88, ByteArray(0)),
            Step(EquipmentId.GENERAL, 0x82, ByteArray(3)),
            Step(EquipmentId.GENERAL, 0x84, ByteArray(2)),
        )
    }

    // --------------------------------------------------------------- control

    /**
     * Unlock and arm the machine so that it accepts control writes.
     *
     * Arming is the step that matters. Without it the machine accepts writes but ignores them,
     * and its physical START button starts a local workout the app cannot take over.
     */
    suspend fun enterControlMode(): Boolean {
        holder.update { it.copy(readiness = MachineReadiness.LOCKED) }

        // No unlock computed yet means the handshake did not finish: run it again.
        val deviceInfo = holder.state.value.deviceInfo?.takeIf { computedUnlock != null } ?: discover()
        // The computed unlock first; a saved code is only the fallback for a console it cannot handle.
        val computed = computedUnlock
        val payload = computed ?: unlockProvider.createUnlockPayload(deviceInfo)?.takeIf { it.isNotEmpty() }
        val source = if (computed != null) "computed from the handshake" else unlockProvider.description
        if (payload == null) {
            val message = "No unlock is available for this treadmill. ${unlockProvider.description}."
            holder.update { it.copy(lastError = message, readiness = MachineReadiness.LOCKED) }
            log.event("unlock unavailable: ${unlockProvider.description}")
            _events.tryEmit(message)
            return false
        }

        log.event("unlock ($source)")
        val unlockFrame = request(CommandId.VERIFY_SECURITY, payload)
        if (!unlockFrame.checksumValid) {
            log.event("unlock response had a bad checksum")
        }

        holder.update {
            it.copy(
                readiness = MachineReadiness.UNLOCKED,
                unlockDescription = source,
            )
        }

        // Read the machine's limits and its startup state before arming. Not fatal: without them
        // the app's own ceilings still apply.
        runCatching { readStartupFields() }
            .onFailure { log.event("startup read failed: ${it.message}") }

        // Arm, in the vendor application's order: start requested on, then idle mode lockout off.
        val startRequested = writeField(FieldId.REQUIRE_START_REQUESTED, FieldValue.Flag(true))
        if (!startRequested) {
            holder.update { it.copy(lastError = "The treadmill did not enter remote-control mode.") }
            log.event("arming failed at RequireStartRequested")
            return false
        }

        val lockout = writeField(FieldId.IDLE_MODE_LOCKOUT, FieldValue.Flag(false))
        if (!lockout) {
            holder.update { it.copy(lastError = "The treadmill did not accept the unlock.") }
            log.event("arming failed at IdleModeLockout")
            return false
        }

        holder.update { it.copy(readiness = MachineReadiness.ARMED, lastError = null) }
        log.event("armed")
        _events.tryEmit("The treadmill is ready to accept commands.")
        return true
    }

    /** Read the machine's limits and startup state. */
    suspend fun readStartupFields() {
        val values = readFields(Polls.STARTUP)
        holder.update { state ->
            state.copy(
                maxSpeedKph = (values[FieldId.MAX_KPH] as? FieldValue.UInt)?.value?.let { it / 100.0 }
                    ?: state.maxSpeedKph,
                minSpeedKph = (values[FieldId.MIN_KPH] as? FieldValue.UInt)?.value?.let { it / 100.0 }
                    ?: state.minSpeedKph,
                // MAX_GRADE / MIN_GRADE decode as an Incline (hundredths already applied), not a
                // plain integer, so both shapes are accepted.
                maxInclinePercent = when (val v = values[FieldId.MAX_GRADE]) {
                    is FieldValue.Incline -> v.percent
                    is FieldValue.SInt -> v.value / 100.0
                    else -> null
                } ?: state.maxInclinePercent,
                minInclinePercent = when (val v = values[FieldId.MIN_GRADE]) {
                    is FieldValue.Incline -> v.percent
                    is FieldValue.SInt -> v.value / 100.0
                    else -> null
                } ?: state.minInclinePercent,
            )
        }
    }

    /** Poll the machine and update the state. */
    /**
     * Who runs the belt (spec 007): speed, incline and pause are only sent during a run OpenFit
     * started itself. [clock] is milliseconds, replaceable for tests.
     */
    val gate = ControlGate()
    var clock: () -> Long = { System.currentTimeMillis() }

    private fun gateRefusal(): LimitCheck.Refused? =
        if (gate.allowsMovement()) null else LimitCheck.Refused(Refusal.NO_APP_WORKOUT)

    suspend fun poll(): TreadmillState {
        val values = readFields(Polls.activeTelemetryMask())
        if (values.isEmpty()) return holder.state.value

        val updated = holder.updateAndGet { state ->
            state.copy(
                targetSpeedKph = (values[FieldId.KPH] as? FieldValue.Speed)?.kmh ?: state.targetSpeedKph,
                targetInclinePercent = (values[FieldId.GRADE] as? FieldValue.Incline)?.percent
                    ?: state.targetInclinePercent,
                actualSpeedKph = (values[FieldId.ACTUAL_KPH] as? FieldValue.Speed)?.kmh
                    ?: state.actualSpeedKph,
                actualInclinePercent = (values[FieldId.ACTUAL_INCLINE] as? FieldValue.Incline)?.percent
                    ?: state.actualInclinePercent,
                distanceMeters = (values[FieldId.CURRENT_DISTANCE] as? FieldValue.UInt)?.value
                    ?: state.distanceMeters,
                elapsedSeconds = (values[FieldId.CURRENT_TIME] as? FieldValue.UInt)?.value
                    ?: state.elapsedSeconds,
                calories = (values[FieldId.CURRENT_CALORIES] as? FieldValue.UInt)?.value?.let { it / 100000.0 }
                    ?: state.calories,
                heartRateBpm = (values[FieldId.PULSE] as? FieldValue.Pulse)?.currentBpm
                    ?: state.heartRateBpm,
                heartRateSource = (values[FieldId.PULSE] as? FieldValue.Pulse)?.source
                    ?: state.heartRateSource,
                workoutMode = (values[FieldId.WORKOUT_MODE] as? FieldValue.WorkoutMode)?.mode
                    ?: state.workoutMode,
            )
        }
        log.attachFields(values)
        when (gate.observe(updated.workoutMode, clock())) {
            ControlGate.Observation.MANUAL_USE -> {
                log.event("the belt is running and OpenFit did not start it: hands off")
                holder.update { it.copy(readiness = MachineReadiness.BUSY_LOCAL) }
            }
            ControlGate.Observation.MANUAL_USE_ENDED -> {
                log.event("manual run ended")
                holder.update { if (it.readiness == MachineReadiness.BUSY_LOCAL) it.copy(readiness = MachineReadiness.READ_ONLY) else it }
            }
            ControlGate.Observation.APP_RUN_ENDED -> log.event("the console ended OpenFit's run (${updated.workoutMode}): no more belt commands")
            ControlGate.Observation.NONE -> Unit
        }
        return holder.state.value
    }

    /**
     * Set the target speed.
     *
     * The ceiling is checked here, in the protocol layer, so that no user interface can get around
     * it.
     */
    suspend fun setSpeed(kmh: Double): LimitCheck {
        gateRefusal()?.let { return it }
        val check = limitsProvider().checkSpeed(kmh)
        if (check is LimitCheck.Refused) return check

        val written = writeField(FieldId.KPH, FieldValue.Speed(kmh))
        if (written) {
            holder.update { it.copy(targetSpeedKph = kmh) }
        }
        return if (written) LimitCheck.Accepted else LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
    }

    /** Set the target incline. */
    suspend fun setIncline(percent: Double): LimitCheck {
        gateRefusal()?.let { return it }
        val check = limitsProvider().checkIncline(percent)
        if (check is LimitCheck.Refused) return check

        val written = writeField(FieldId.GRADE, FieldValue.Incline(percent))
        if (written) {
            holder.update { it.copy(targetInclinePercent = percent) }
        }
        return if (written) LimitCheck.Accepted else LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
    }

    /**
     * Set the target speed and the target incline together.
     *
     * The machine expects both in a single write when they change at the same moment, which is
     * exactly what a workout step does. Writing them separately would let the machine apply a
     * speed the ceiling forbids in between. Evidence: research.md section 8.
     */
    suspend fun setSpeedAndIncline(speedKph: Double, inclinePercent: Double): LimitCheck {
        gateRefusal()?.let { return it }
        val check = limitsProvider().check(speedKph, inclinePercent, holder.state.value.readiness)
        if (check is LimitCheck.Refused) return check

        val content = FieldCodec.encodeWriteSection(
            listOf(
                FieldId.KPH to FieldValue.Speed(speedKph),
                FieldId.GRADE to FieldValue.Incline(inclinePercent),
            ),
        )
        val written = sendWrite(content)
        if (written) {
            holder.update {
                it.copy(
                    targetSpeedKph = speedKph,
                    targetInclinePercent = inclinePercent,
                )
            }
        }
        return if (written) LimitCheck.Accepted else LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
    }

    /**
     * Start a workout at the given speed and incline.
     *
     * The machine is given speed, incline, and mode together, which is what the vendor application
     * does.
     */
    suspend fun start(speedKph: Double, inclinePercent: Double): LimitCheck {
        // Take control on demand, so starting is one tap. Not at connect: an armed console hands
        // its own START button to the app, which would get in the way of using it by hand.
        val before = holder.state.value
        // Someone is using the treadmill by hand: OpenFit does not take the belt from them.
        if (gate.manualUse) return LimitCheck.Refused(Refusal.MACHINE_BUSY_LOCAL)
        if (before.connectionState == ConnectionState.CONNECTED &&
            before.readiness != MachineReadiness.ARMED &&
            before.readiness != MachineReadiness.BUSY_LOCAL
        ) {
            // Taking control re-runs the handshake when it has not completed, which is also what
            // computes the unlock, so this is tried even when no unlock is known yet.
            log.event("not armed: taking control before the start")
            runCatching { enterControlMode() }.onFailure { log.event("taking control failed: ${it.message}") }
        }

        val check = limitsProvider().check(speedKph, inclinePercent, holder.state.value.readiness)
        if (check is LimitCheck.Refused) return check

        // The console refuses a start while it shows the results of the last workout (status 04,
        // seen live 2026-11-23), and can stay there for over a minute. Ask it back to idle first.
        if (holder.state.value.workoutMode == WorkoutMode.RESULTS) {
            val idle = writeField(FieldId.WORKOUT_MODE, FieldValue.WorkoutMode(WorkoutMode.IDLE))
            log.event("results -> idle before start: ${if (idle) "accepted" else "refused"}")
        }

        val content = FieldCodec.encodeWriteSection(
            listOf(
                FieldId.KPH to FieldValue.Speed(speedKph),
                FieldId.GRADE to FieldValue.Incline(inclinePercent),
                FieldId.WORKOUT_MODE to FieldValue.WorkoutMode(WorkoutMode.RUNNING),
            ),
        )
        val written = sendWrite(content)
        if (written) {
            holder.update {
                it.copy(
                    targetSpeedKph = speedKph,
                    targetInclinePercent = inclinePercent,
                    workoutMode = WorkoutMode.RUNNING,
                )
            }
            gate.onAppStart(clock())
        }
        return if (written) LimitCheck.Accepted else LimitCheck.Refused(Refusal.MACHINE_NOT_ARMED)
    }

    /** Pause the workout: the console's PAUSE mode stops the belt and keeps the workout open. */
    suspend fun pause(): Boolean {
        if (!gate.allowsMovement()) {
            log.event("pause not sent: OpenFit is not running the belt")
            return false
        }
        val written = writeField(FieldId.WORKOUT_MODE, FieldValue.WorkoutMode(WorkoutMode.PAUSE))
        if (written) holder.update { it.copy(workoutMode = WorkoutMode.PAUSE) }
        log.event("pause requested: ${if (written) "accepted" else "refused"}")
        return written
    }

    /** Stop the workout. The machine is moved to its results state. */
    suspend fun stop(): Boolean {
        val content = FieldCodec.encodeWriteSection(
            listOf(FieldId.WORKOUT_MODE to FieldValue.WorkoutMode(WorkoutMode.RESULTS)),
        )
        val written = sendWrite(content)
        if (written) {
            holder.update { it.copy(workoutMode = WorkoutMode.RESULTS) }
            gate.onAppStop()
        }
        log.event("stop requested: ${if (written) "accepted" else "refused"}")
        if (written) return true
        // A console that is not running refuses the stop (status 04), and that is fine: check what
        // it says it is doing. Only a belt that is still running (or paused) is a failed stop.
        val mode = runCatching { poll().workoutMode }.getOrNull()
        log.event("after refused stop, the console reports $mode")
        return mode != null && mode != WorkoutMode.RUNNING && mode != WorkoutMode.PAUSE
    }

    /**
     * Hand the console back to its own buttons (spec 007): undo the arming write
     * (RequireStartRequested) when the belt is not moving, then report read-only. Best effort: the
     * link is dropped right after anyway.
     */
    suspend fun releaseControl(): Boolean {
        gate.onAppStop()
        val state = holder.state.value
        if (state.workoutMode == WorkoutMode.RUNNING || state.workoutMode == WorkoutMode.PAUSE) return false
        if (state.readiness != MachineReadiness.ARMED) return true
        val released = runCatching { writeField(FieldId.REQUIRE_START_REQUESTED, FieldValue.Flag(false)) }.getOrDefault(false)
        log.event("release control: ${if (released) "accepted" else "refused"}")
        holder.update { it.copy(readiness = MachineReadiness.READ_ONLY) }
        return released
    }

    // --------------------------------------------------------------- writing

    /** Write a single field. */
    suspend fun writeField(field: FieldId, value: FieldValue): Boolean =
        sendWrite(FieldCodec.encodeWriteSection(listOf(field to value)))

    /** Send a write section and report whether the machine accepted it. */
    private suspend fun sendWrite(writeSection: ByteArray): Boolean {
        // A ReadWriteData command always carries both sections; a write-only one ends with an
        // empty read section (`00`). Without it the console does not answer and drops the link
        // (captured from the vendor application, and seen live, 2026-11-23).
        val content = writeSection + FieldCodec.encodeReadSection(emptyList())
        val frame = request(CommandId.READ_WRITE_DATA, content)
        if (!frame.checksumValid) {
            log.event("write response had a bad checksum")
            return false
        }
        val status = frame.content.firstOrNull()?.toInt()?.and(0xFF)
        if (status == CommandStatus.SECURITY_BLOCK) {
            log.event("machine reported SecurityBlock, re-unlocking")
            holder.update { it.copy(readiness = MachineReadiness.LOCKED) }
            if (computedUnlock != null || unlockProvider.isAvailable) {
                enterControlMode()
                return true
            }
            return false
        }
        return status == null || status == CommandStatus.DONE
    }

    /** Read a set of fields. */
    suspend fun readFields(ids: List<Int>): Map<FieldId, FieldValue> {
        val content = FieldCodec.encodeWriteSection(emptyList()) + FieldCodec.encodeReadSection(ids)
        val frame = request(CommandId.READ_WRITE_DATA, content)
        if (!frame.checksumValid) return emptyMap()

        // The response carries the status byte first, then the values in ascending field order.
        if (frame.content.isEmpty()) return emptyMap()
        val values = frame.content.copyOfRange(1, frame.content.size)
        return decodeValues(ids, values)
    }

    /** Decode a sequence of values in ascending field id order. */
    private fun decodeValues(ids: List<Int>, payload: ByteArray): Map<FieldId, FieldValue> {
        val values = mutableMapOf<FieldId, FieldValue>()
        var offset = 0
        for (id in ids) {
            val field = FieldId.of(id) ?: continue
            if (offset + field.size > payload.size) break
            val raw = payload.copyOfRange(offset, offset + field.size)
            offset += field.size
            FieldCodec.decodeValue(field, raw)?.let { values[field] = it }
        }
        return values
    }

    // ----------------------------------------------------------- request flow

    /** Send a command and wait for its response. */
    suspend fun request(
        command: Int,
        content: ByteArray,
        timeoutMillis: Long = 5000,
        device: Int = EquipmentId.TREADMILL,
    ): CommandFrame =
        // One request at a time, held until it is answered: the machine answers in order, and a
        // poll sent while the handshake waits would otherwise take over the pending slot.
        requestMutex.withLock {
            val deferred = CompletableDeferred<CommandFrame>()
            pending = PendingRequest(command, deferred)
            val frame = Command.encode(device, command, content)
            log.outbound(frame, command)
            transport.write(frame)
            withTimeoutOrNull(timeoutMillis) { deferred.await() }
                ?: run {
                    pending = null
                    throw SessionTimeout("the machine did not answer command ${CommandId.nameOf(command)}")
                }
        }

    /** Handle one inbound notification. */
    private fun onNotification(bytes: ByteArray) {
        val frame = runCatching { Command.decode(bytes) }.getOrNull() ?: return

        val waiting = pending
        if (waiting != null && frame.command == waiting.command) {
            // Clear the slot BEFORE waking the waiter. Completing it resumes the request at once,
            // which lets the next request take the slot; clearing afterwards erased that next
            // request, so its reply matched nothing and it timed out (seen live, 2026-11-23,
            // with the belt running).
            pending = null
            waiting.deferred.complete(frame)
        }
    }

    /** A request that is waiting for its response. */
    private class PendingRequest(
        val command: Int,
        val deferred: CompletableDeferred<CommandFrame>,
    )

    /** Query helpers used during discovery. */
    private suspend fun queryUInt32(command: Int, from: Int, until: Int): Long? =
        queryBytes(command, from, until)?.let { bytes ->
            var value = 0L
            for (i in bytes.indices.reversed()) {
                value = (value shl 8) or (bytes[i].toLong() and 0xFF)
            }
            value
        }

    private suspend fun queryUInt8(command: Int, index: Int): Int? =
        queryBytes(command, index, index + 1)?.firstOrNull()?.toInt()?.and(0xFF)

    private suspend fun queryBytes(command: Int, from: Int, until: Int): ByteArray? {
        val frame = runCatching { request(command, ByteArray(2)) }.getOrNull() ?: return null
        if (!frame.checksumValid) return null
        val content = frame.content
        if (content.size < until) return null
        return content.copyOfRange(from, until)
    }

    private suspend fun querySupportedIds(command: Int): List<Int> {
        val frame = runCatching { request(command, ByteArray(0)) }.getOrNull() ?: return emptyList()
        if (!frame.checksumValid) return emptyList()
        val content = frame.content
        if (content.isEmpty()) return emptyList()
        val count = content[0].toInt() and 0xFF
        return content.copyOfRange(1, minOf(1 + count, content.size)).map { it.toInt() and 0xFF }
    }

    private suspend fun queryFirmwareVersion(): String? {
        val frame = runCatching { request(CommandId.VERSION_INFO, ByteArray(2)) }.getOrNull() ?: return null
        if (!frame.checksumValid) return null
        val content = frame.content
        if (content.size <= 11) return null
        return content.copyOfRange(11, content.size)
            .takeWhile { it.toInt() != 0x01 && it.toInt() != 0x00 }
            .map { it.toInt().toChar() }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }
}

/** The status byte a command response carries. */
object CommandStatus {
    const val DEV_NOT_SUPPORTED = 0
    const val CMD_NOT_SUPPORTED = 1
    const val DONE = 2
    const val IN_PROGRESS = 3
    const val FAILED = 4
    const val TIME_LEFT = 5
    const val UNKNOWN_FAILURE = 7
    const val SECURITY_BLOCK = 8
    const val COMM_FAILED = 9
}

/** Raised when the machine does not answer in time. */
class SessionTimeout(message: String) : Exception(message)
