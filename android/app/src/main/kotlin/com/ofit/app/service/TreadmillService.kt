package com.ofit.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ofit.app.MainActivity
import com.ofit.app.R
import com.ofit.app.TreadmillApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the connection to the treadmill alive while the app is in the background.
 *
 * The notification is required for a foreground service, and it doubles as a way back into the
 * app. The service does not own the session; it only keeps the process alive and shows the
 * current state.
 *
 * The service is started when a treadmill is connected, not when the application opens. A
 * foreground service of type `connectedDevice` needs the runtime Bluetooth permission, which has
 * not been granted when the application object is created: starting one there throws a
 * `SecurityException` and takes the app down before the first screen appears.
 */
class TreadmillService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("Ready to connect"), type())
        }
        if (started.isFailure) {
            // The system refused the foreground service. That is not worth crashing over: the
            // connection still works while the app is open, and the user is told why the
            // background behaviour is limited.
            Log.w(TreadmillApplication.TAG, "the foreground service was refused", started.exceptionOrNull())
            report("The system would not keep the connection alive in the background.")
            stopSelf()
            return START_NOT_STICKY
        }

        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val state = (application as TreadmillApplication).controller.state.value
                val text = when {
                    state.connectionState == com.ofit.protocol.ConnectionState.CONNECTED ->
                        "Connected to the treadmill"
                    state.connectionState == com.ofit.protocol.ConnectionState.CONNECTING ->
                        "Connecting to the treadmill"
                    else -> "Ready to connect"
                }
                updateNotification(text)
                delay(1000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Tell the user, through the controller, what went wrong. */
    private fun report(message: String) {
        val application = application as? TreadmillApplication ?: return
        application.controller.showMessage(message)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, TreadmillApplication.CHANNEL_ID)
            .setContentTitle("ofit")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** The foreground service type, which the manifest also declares. */
    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

    companion object {
        private const val NOTIFICATION_ID = 1001

        /**
         * Start the service.
         *
         * A refusal is not fatal: the app is still usable, just without the background keep-alive.
         */
        fun start(context: Context) {
            val intent = Intent(context, TreadmillService::class.java)
            runCatching { context.startForegroundService(intent) }
                .onFailure { error ->
                    Log.w(TreadmillApplication.TAG, "could not start the foreground service", error)
                }
        }

        /** Stop the service. */
        fun stop(context: Context) {
            val intent = Intent(context, TreadmillService::class.java)
            runCatching { context.stopService(intent) }
        }
    }
}
