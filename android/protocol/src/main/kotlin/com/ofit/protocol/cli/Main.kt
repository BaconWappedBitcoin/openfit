package com.ofit.protocol.cli

import com.ofit.protocol.Json
import com.ofit.protocol.Command
import com.ofit.protocol.CommandId
import com.ofit.protocol.EquipmentId
import com.ofit.protocol.FieldCodec
import com.ofit.protocol.FieldId
import com.ofit.protocol.FieldValue
import com.ofit.protocol.Framing
import com.ofit.protocol.Gatt
import com.ofit.protocol.Polls
import com.ofit.protocol.ProgramValidation
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.Wire
import com.ofit.protocol.WorkoutCodec
import com.ofit.protocol.WorkoutProgram

/**
 * The protocol command line.
 *
 * Constitution Article II: every library must expose its functionality through a command line, so
 * that the wire format can be inspected and replayed from a terminal without a phone.
 *
 * ```text
 * protocol fields                      list the field table
 * protocol commands                    list the command table
 * protocol gatt                        show the GATT identifiers
 * protocol checksum <hex>              checksum a byte sequence
 * protocol encode --speed 8 --incline 2
 * protocol poll                        build the telemetry poll request
 * protocol frame <hex>                 split a payload into BLE chunks
 * protocol decode <hex>                describe a command frame
 * protocol program validate <file>     check a workout program against the ceilings
 * protocol program timeline <file>     show a workout program's schedule
 * ```
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    val rest = args.drop(1)
    val asJson = rest.contains("--json")
    val positional = rest.filterNot { it.startsWith("--") }

    val result: Int = when (command) {
        "fields" -> listFields(asJson)
        "commands" -> listCommands(asJson)
        "gatt" -> listGatt(asJson)
        "checksum" -> checksum(positional.firstOrNull())
        "encode" -> encode(rest)
        "poll" -> poll(asJson)
        "frame" -> frame(positional.firstOrNull())
        "decode" -> decode(positional.firstOrNull())
        "program" -> program(rest)
        "help", "--help", "-h" -> {
            printUsage()
            0
        }

        else -> {
            System.err.println("unknown command: $command")
            printUsage()
            2
        }
    }

    if (result != 0) System.exit(result)
}

private fun printUsage() {
    println(
        """
        protocol - the iFit treadmill wire format

        Usage:
          protocol fields [--json]                 list the field table
          protocol commands [--json]               list the command table
          protocol gatt [--json]                   show the GATT identifiers
          protocol checksum <hex>                  checksum a byte sequence
          protocol encode --speed <kph> [--incline <pct>]
          protocol poll [--json]                   build the telemetry poll request
          protocol frame <hex>                     split a payload into BLE chunks
          protocol decode <hex>                    describe a command frame
          protocol program validate <file>         check a workout program against the ceilings
          protocol program timeline <file>         show a workout program's schedule
        """.trimIndent(),
    )
}

private fun listFields(asJson: Boolean): Int {
    if (asJson) {
        println(FieldId.entries.joinToString(",\n", "[", "]") { field ->
            """{"id":${field.id},"name":"${field.name}","size":${field.size},"signed":${field.signed}}"""
        })
    } else {
        println("id  size  signed  name")
        for (field in FieldId.entries) {
            println("%3d %5d %6s  %s".format(field.id, field.size, field.signed, field.name))
        }
    }
    return 0
}

private fun listCommands(asJson: Boolean): Int {
    val names = CommandId.NAMES.entries.sortedBy { it.key }
    if (asJson) {
        println(names.joinToString(",\n", "[", "]") { """{"id":${it.key},"name":"${it.value}"}""" })
    } else {
        println("id   name")
        for ((id, name) in names) {
            println("0x%02x %s".format(id, name))
        }
    }
    return 0
}

private fun listGatt(asJson: Boolean): Int {
    val entries = mapOf(
        "service" to Gatt.CONSOLE_SERVICE,
        "host_write" to Gatt.HOST_WRITE,
        "machine_notify" to Gatt.MACHINE_NOTIFY,
        "advertised_name" to Gatt.ADVERTISED_NAME,
        "mtu" to Gatt.MTU.toString(),
    )
    if (asJson) {
        println(entries.entries.joinToString(",\n", "{", "}") { "\"${it.key}\":\"${it.value}\"" })
    } else {
        for ((key, value) in entries) {
            println("%-16s %s".format(key, value))
        }
    }
    return 0
}

private fun checksum(hex: String?): Int {
    val bytes = hex?.let { parseHex(it) }
    if (bytes == null) {
        System.err.println("usage: protocol checksum <hex>")
        return 2
    }
    println(Command.checksum(bytes))
    return 0
}

private fun encode(args: List<String>): Int {
    val speed = optionValue(args, "--speed")?.toDoubleOrNull()
    val incline = optionValue(args, "--incline")?.toDoubleOrNull()
    if (speed == null && incline == null) {
        System.err.println("usage: protocol encode --speed <kph> [--incline <pct>]")
        return 2
    }

    val values = buildList {
        speed?.let { add(FieldId.KPH to FieldValue.Speed(it)) }
        incline?.let { add(FieldId.GRADE to FieldValue.Incline(it)) }
    }
    val content = FieldCodec.encodeWriteSection(values)
    val frame = Command.encode(EquipmentId.TREADMILL, CommandId.READ_WRITE_DATA, content)

    println("command : ${Framing.toHex(frame)}")
    println("content : ${Framing.toHex(content)}")
    println("checksum: ${Command.checksum(frame, frame.size - 1)}")
    printChunks(frame)
    return 0
}

private fun poll(asJson: Boolean): Int {
    val content = FieldCodec.encodeWriteSection(emptyList()) + FieldCodec.encodeReadSection(Polls.TELEMETRY)
    val frame = Command.encode(EquipmentId.TREADMILL, CommandId.READ_WRITE_DATA, content)

    if (asJson) {
        println("""{"command":"${Framing.toHex(frame)}","fields":${Polls.TELEMETRY}}""")
    } else {
        println("poll request command : ${Framing.toHex(frame)}")
        println("read fields          : ${Polls.TELEMETRY.joinToString(", ")}")
        printChunks(frame)
    }
    return 0
}

private fun frame(hex: String?): Int {
    val bytes = hex?.let { parseHex(it) }
    if (bytes == null) {
        System.err.println("usage: protocol frame <hex>")
        return 2
    }
    printChunks(bytes)
    return 0
}

private fun decode(hex: String?): Int {
    val bytes = hex?.let { parseHex(it) }
    if (bytes == null) {
        System.err.println("usage: protocol decode <hex>")
        return 2
    }
    println(Command.describe(bytes))
    val frame = runCatching { Command.decode(bytes) }.getOrNull()
    if (frame != null && frame.command == CommandId.READ_WRITE_DATA) {
        val values = FieldCodec.decodeWriteSection(frame.content)
        if (values.isNotEmpty()) {
            for ((field, value) in values) {
                println("  ${field.name} = $value")
            }
        }
    }
    return 0
}

/**
 * Inspect a workout program.
 *
 * ```text
 * protocol program validate <file> [--max-speed <kph>] [--max-incline <pct>]
 * protocol program timeline <file>
 * ```
 */
private fun program(args: List<String>): Int {
    val action = args.firstOrNull { !it.startsWith("--") }
    val file = args.filterNot { it.startsWith("--") }.getOrNull(1)
    if (action == null || file == null) {
        System.err.println("usage: protocol program <validate|timeline> <file>")
        return 2
    }

    val text = runCatching { java.io.File(file).readText() }.getOrElse { error ->
        System.err.println("could not read $file: ${error.message}")
        return 2
    }

    val workout = runCatching { WorkoutCodec.decode(text) }.getOrElse { error ->
        System.err.println("could not read the program: ${error.message}")
        return 2
    }

    return when (action) {
        "validate" -> validateProgram(workout, args)
        "timeline" -> timelineProgram(workout)
        else -> {
            System.err.println("unknown program action: $action")
            2
        }
    }
}

private fun validateProgram(workout: WorkoutProgram, args: List<String>): Int {
    val limits = SafetyLimits.DEFAULT.copy(
        maxSpeedKph = optionValue(args, "--max-speed")?.toDoubleOrNull() ?: SafetyLimits.DEFAULT.maxSpeedKph,
        maxInclinePercent = optionValue(args, "--max-incline")?.toDoubleOrNull()
            ?: SafetyLimits.DEFAULT.maxInclinePercent,
    )

    val validation = workout.validate(limits)
    val problems = validation.allProblems
    if (problems.isEmpty()) {
        println("ok: ${workout.name} - ${workout.steps.size} steps, ${workout.totalDurationSeconds}s")
        return 0
    }

    println("rejected: ${workout.name}")
    for (problem in problems) {
        val where = problem.stepIndex?.let { "step ${it + 1}" } ?: "program"
        println("  $where: ${problem.message}")
    }
    return 1
}

private fun timelineProgram(workout: WorkoutProgram): Int {
    println(workout.name)
    println("  total ${workout.totalDurationSeconds}s in ${workout.steps.size} steps")
    var elapsed = 0
    workout.steps.forEachIndexed { index, step ->
        println(
            "  %2d  %5ds - %5ds  %4.1f km/h  %4.1f %%".format(
                index + 1,
                elapsed,
                elapsed + step.durationSeconds,
                step.speedKph,
                step.inclinePercent,
            ),
        )
        elapsed += step.durationSeconds
    }
    return 0
}

/** The options that take a value, so that their values are not mistaken for arguments. */
private val VALUE_OPTIONS = setOf(
    "--user",
    "--password",
    "--session",
    "--token",
    "--base",
    "--page",
    "--page-size",
    "--owner",
    "--output",
    "--output-dir",
    "--max-speed",
    "--max-incline",
    "--software-number",
    "--batch",
    "--limit",
)

/** The arguments that are not options, and not an option's value. */
private fun positionals(args: List<String>): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (argument.startsWith("--")) {
            if (argument in VALUE_OPTIONS) index++
        } else {
            result += argument
        }
        index++
    }
    return result
}

private fun printChunks(payload: ByteArray) {
    println("chunks  :")
    Wire.chunks(payload).forEachIndexed { index, chunk ->
        println("  [$index] ${Framing.toHex(chunk)} (${chunk.size} bytes)")
    }
}

private fun optionValue(args: List<String>, name: String): String? {
    val index = args.indexOf(name)
    if (index < 0 || index + 1 >= args.size) return null
    return args[index + 1]
}

private fun parseHex(text: String): ByteArray {
    val cleaned = text.filterNot { it.isWhitespace() || it == '-' || it == ':' }
    require(cleaned.length % 2 == 0) { "hex needs an even number of digits" }
    return ByteArray(cleaned.length / 2) { index ->
        val high = cleaned[index * 2].digitToIntOrNull(16)
        val low = cleaned[index * 2 + 1].digitToIntOrNull(16)
        require(high != null && low != null) { "not valid hex: $text" }
        ((high shl 4) or low).toByte()
    }
}
