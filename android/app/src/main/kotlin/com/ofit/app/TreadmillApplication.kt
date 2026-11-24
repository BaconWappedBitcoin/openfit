package com.ofit.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Holds the single long-lived controller.
 *
 * The session must outlive any one screen: a workout continues while the app is in the background
 * and the screen is off. Specification requirement FR-020.
 */
class TreadmillApplication : Application() {

    /** The controller, created once for the life of the process. */
    lateinit var controller: TreadmillController
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else, so that a crash during start-up is written down.
        CrashLog.install(this)
        controller = TreadmillController(this)
        createNotificationChannel()
        // The foreground service is deliberately not started here. A foreground service of type
        // "connectedDevice" needs the runtime Bluetooth permission, which has not been asked for
        // yet when the application object is created, and starting one without it throws and takes
        // the whole app down. The service starts when a treadmill is actually connected, which is
        // the first moment it is needed.
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Treadmill connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the connection to the treadmill alive while you work out."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ofit.treadmill"

        /** The tag for anything the application logs. */
        const val TAG = "ofit"
    }
}

/**
 * A failure that should be shown to the user rather than swallowed.
 *
 * Logging and carrying on is the right behaviour for a problem the app can work around, such as a
 * foreground service the system will not allow.
 */
internal fun logAndContinue(message: String, error: Throwable) {
    Log.w(TreadmillApplication.TAG, message, error)
}
