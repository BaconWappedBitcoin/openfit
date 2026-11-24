package com.ofit.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The transport contract.
 *
 * The protocol library owns the wire format and knows nothing about Bluetooth. A transport moves
 * bytes to and from the machine. Keeping the boundary here is what lets the wire format be tested
 * on a desktop JVM against recorded vectors.

 */
interface Transport {

    /** The current link state. */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Bytes arriving from the machine.
     *
     * A single notification may carry only part of a chunk group; the session reassembles.
     */
    val notifications: Flow<ByteArray>

    /**
     * Send one payload to the machine.
     *
     * Implementations chunk the payload to the 20 byte transfer unit themselves, because the
     * transport is what knows the negotiated MTU.
     */
    suspend fun write(payload: ByteArray)

    /** Open the link. */
    suspend fun connect()

    /** Close the link. */
    suspend fun disconnect()
}

/** The link state, as the user sees it. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED,
}

/**
 * The GATT identifiers of the treadmill's console service.
 *
 * The characteristic names describe the direction of the data, not a RX or TX role, because the
 * two public references disagreed about which way round the labels were. The direct GATT capture
 * is authoritative: the host writes to `HOST_WRITE` and the machine notifies on
 * `MACHINE_NOTIFY`.
 *
 * Evidence: research.md section 3.1.
 */
object Gatt {

    /** The console service. */
    const val CONSOLE_SERVICE: String = "00001533-1412-efde-1523-785feabcd123"

    /** The characteristic the host writes commands to. */
    const val HOST_WRITE: String = "00001534-1412-efde-1523-785feabcd123"

    /** The characteristic the machine notifies state on. */
    const val MACHINE_NOTIFY: String = "00001535-1412-efde-1523-785feabcd123"

    /**
     * A secondary service some consoles expose.
     *
     * Not yet characterised. Gated behind `FeatureFlags.auxiliaryService`.
     */
    const val AUXILIARY_SERVICE: String = "00001530-1212-efde-1523-785feabcd123"

    /** The name the treadmill advertises under. */
    const val ADVERTISED_NAME: String = "I_TL"

    /** The negotiated transfer unit. */
    const val MTU: Int = Framing.MTU

    /**
     * Names iFit consoles advertise.
     *
     * A device is treated as a console when its **uppercased** name starts with one of these. The
     * treadmill this project targets is `I_TL`; the rest are other iFit machines in the same family.
     *
     * Matching is by **prefix**, which is a
     * deliberate widening: a console that appends anything to its name — a pairing code, a
     * serial, a suffix — is still a console, and missing it is the one failure that matters here.
     *
     * Evidence: research.md section 3.3.
     */
    val CONSOLE_NAMES: List<String> = listOf(
        "ELLIP",      // elliptical
        "TREAD",
        "BIKE",
        "STRIDE",
        "I_TL",       // treadmill
        "I_EB",
        "I_SB",
        "I_EL",
        "I_VE",
        "I_IT",
        "I_FS",
        "I_RW",
        "FITPRO SUPPORTED",
    )

    /**
     * Whether a Bluetooth name looks like an iFit console.
     *
     * A name that is not on the list is not rejected outright. It is only *not a console*, and the
     * user may still pick it: the app lists every device it can see, so that a machine whose name
     * is not in this list can still be reached.
     */
    fun looksLikeConsole(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return CONSOLE_NAMES.any { upper.startsWith(it) }
    }
}

/**
 * Feature flags for behaviour that has not been confirmed against the user's machine.
 *
 * Everything unverified defaults to off, per constitution Article V.
 */
object FeatureFlags {

    /** Attempt the published unlock computation. The hash construction is not fully published. */
    var unlockComputed: Boolean = false

    /** Accept a user supplied or captured unlock payload. */
    var unlockSupplied: Boolean = true

    /** Poll the secondary and button-watch masks, which report key presses and volume. */
    var pollExtendedMasks: Boolean = false

    /** Read the observed speed where the machine populates it, falling back to the target. */
    var readActualKph: Boolean = true

    /** Expose undecoded field values for diagnosis. */
    var rawFieldViewer: Boolean = false

    /** Use the secondary console service. */
    var auxiliaryService: Boolean = false
}
