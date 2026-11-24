package com.ofit.protocol

/**
 * Protocol-level constants from Bluetooth captures of the console and the public reverse-engineering
 * work.
 */

/** Equipment identifier, byte 0 of a FitPro command frame. */
object EquipmentId {
    const val GENERAL = 0x02
    const val TREADMILL = 0x04
}

/**
 * Command identifier, byte 2 of a FitPro command frame.
 *
 * These are the FitPro command values.
 */
object CommandId {
    const val READ_WRITE_DATA = 0x02
    const val CONNECT = 0x04
    const val DISCONNECT = 0x05
    const val CALIBRATE = 0x06
    const val SUPPORTED_DEVICES = 0x80
    const val DEVICE_INFO = 0x81
    const val SYSTEM_INFO = 0x82
    const val TASK_INFO = 0x83
    const val VERSION_INFO = 0x84
    const val MODE_HISTORY = 0x86
    const val SUPPORTED_COMMANDS = 0x88
    const val READ_CONFIG = 0x89
    const val VERIFY_SECURITY = 0x90
    const val PROTOCOL_DATA = 0x91
    const val SPEED_GRADE_LIMIT = 0x92
    const val SERIAL_NUMBER = 0x95

    val NAMES: Map<Int, String> = mapOf(
        READ_WRITE_DATA to "ReadWriteData",
        CONNECT to "Connect",
        DISCONNECT to "Disconnect",
        CALIBRATE to "Calibrate",
        SUPPORTED_DEVICES to "SupportedDevices",
        DEVICE_INFO to "DeviceInfo",
        SYSTEM_INFO to "SystemInfo",
        TASK_INFO to "TaskInfo",
        VERSION_INFO to "VersionInfo",
        MODE_HISTORY to "ModeHistory",
        SUPPORTED_COMMANDS to "SupportedCommands",
        READ_CONFIG to "ReadConfig",
        VERIFY_SECURITY to "VerifySecurity",
        PROTOCOL_DATA to "ProtocolData",
        SPEED_GRADE_LIMIT to "SpeedGradeLimit",
        SERIAL_NUMBER to "SerialNumber",
    )

    fun nameOf(id: Int): String = NAMES[id] ?: "cmd%02x".format(id)
}

/**
 * Whether the machine is merely connected or actually able to accept control writes.
 *
 * The distinction matters: a plain connect leaves the machine in local-control mode, so the
 * physical buttons keep working but the app cannot drive it.
 */
enum class MachineReadiness {
    /** Not yet determined. */
    UNKNOWN,

    /** Connected and polling. Writes are refused; the machine's own buttons work normally. */
    READ_ONLY,

    /** The machine requires an unlock that has not been supplied or computed. */
    LOCKED,

    /** Unlocked, but not yet armed. */
    UNLOCKED,

    /** Ready to accept speed, incline, and mode writes. */
    ARMED,

    /** The machine is running its own on-board workout and ignores app writes. */
    BUSY_LOCAL,

    /** The machine reported an error state. */
    ERRORED,
}
