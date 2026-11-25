package com.ofit.app.history

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.ofit.protocol.HealthExport
import com.ofit.protocol.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.StateFlow as SF
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect sync (P17): each finished workout is written as an exercise session with its
 * distance, calories, climb, heart rate and speed, so it shows up in Google's health apps (Fitbit,
 * Google Fit) and anything else that reads Health Connect. Off until the runner turns it on and
 * grants access. Records carry a stable client id, so sending again replaces rather than duplicates.
 */
class HealthConnectSync(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ofit_health", Context.MODE_PRIVATE)

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
    )

    private val KINDS = listOf(
        "session" to ExerciseSessionRecord::class, "distance" to DistanceRecord::class, "calories" to TotalCaloriesBurnedRecord::class,
        "climb" to ElevationGainedRecord::class, "heart" to HeartRateRecord::class, "speed" to SpeedRecord::class,
    )

    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled: StateFlow<Boolean> = _enabled

    /** What happened last, for the settings dialog. */
    private val _status = MutableStateFlow<String?>(null)
    val status: SF<String?> = _status

    val available: Boolean
        get() = HealthConnectClient.getSdkStatus(app) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? = if (available) HealthConnectClient.getOrCreate(app) else null

    suspend fun hasPermissions(): Boolean =
        client()?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean("enabled", on).apply()
    }

    /** Called with History's entries whenever they change: sends the ones newer than the last sent. */
    fun onHistory(entries: List<HistoryEntry>) {
        if (!_enabled.value) return
        val since = prefs.getLong("sentUpTo", System.currentTimeMillis() - 60 * 60_000L)
        val fresh = entries.filter { it.startedAtMillis > since }
        if (fresh.isEmpty()) return
        scope.launch { send(fresh) }
    }

    /** Send these workouts now (also "send past workouts"). Returns how many were written. */
    suspend fun send(entries: List<HistoryEntry>): Int {
        val c = client() ?: run { _status.value = "Health Connect is not available on this device."; return 0 }
        if (!hasPermissions()) { _status.value = "OpenFit needs permission to write to Health Connect."; return 0 }
        val sessions = entries.mapNotNull { HealthExport.of(it) }
        var written = 0
        sessions.forEach { s ->
            runCatching { c.insertRecords(records(s)) }
                .onSuccess { written++ }
                .onFailure { _status.value = "Could not write to Health Connect: ${it.message}" }
        }
        val newest = entries.maxOfOrNull { it.startedAtMillis }
        if (newest != null && newest > prefs.getLong("sentUpTo", 0)) prefs.edit().putLong("sentUpTo", newest).apply()
        if (written > 0) _status.value = "Sent $written workout${if (written == 1) "" else "s"} to Health Connect."
        return written
    }

    /** Delete what was written for the workout that started at [startedAtMillis] (a deleted History entry). */
    fun remove(startedAtMillis: Long) {
        scope.launch {
            val c = client() ?: return@launch
            val id = "openfit-$startedAtMillis"
            KINDS.forEach { (kind, type) ->
                runCatching { c.deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = listOf("$id-$kind")) }
            }
        }
    }

    private fun records(s: HealthExport.Session): List<Record> {
        val start = Instant.ofEpochMilli(s.startMillis)
        val end = Instant.ofEpochMilli(s.endMillis)
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val device = Device(type = Device.TYPE_UNKNOWN, manufacturer = null, model = "OpenFit treadmill")
        fun meta(kind: String) = Metadata.activelyRecorded(device = device, clientRecordId = "${s.clientId}-$kind", clientRecordVersion = 1)
        return buildList {
            add(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = offset, endTime = end, endZoneOffset = offset,
                    exerciseType = if (s.running) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL else ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    title = s.title,
                    metadata = meta("session"),
                ),
            )
            if (s.distanceMeters > 0) add(DistanceRecord(start, offset, end, offset, Length.meters(s.distanceMeters), meta("distance")))
            s.kcal?.let { add(TotalCaloriesBurnedRecord(start, offset, end, offset, Energy.kilocalories(it), meta("calories"))) }
            if (s.climbMeters > 0) add(ElevationGainedRecord(start, offset, end, offset, Length.meters(s.climbMeters), meta("climb")))
            if (s.heartRate.isNotEmpty()) {
                add(HeartRateRecord(start, offset, end, offset, s.heartRate.map { (t, bpm) -> HeartRateRecord.Sample(Instant.ofEpochMilli(t), bpm.toLong()) }, meta("heart")))
            }
            if (s.speed.isNotEmpty()) {
                add(SpeedRecord(start, offset, end, offset, s.speed.map { (t, mps) -> SpeedRecord.Sample(Instant.ofEpochMilli(t), Velocity.metersPerSecond(mps)) }, meta("speed")))
            }
        }
    }
}
