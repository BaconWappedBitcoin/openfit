package com.ofit.app.workout

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ofit.app.MainActivity
import com.ofit.app.R
import com.ofit.app.TreadmillApplication
import com.ofit.protocol.TrainingPlans
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The training plan someone follows (P11/P12), one per profile, kept in prefs `ofit_plan` under
 * the profile's key. Only the choice is stored; progress comes from History.
 */
object PlanStore {
    private const val PREFS = "ofit_plan"

    fun load(context: Context, profileKey: String): TrainingPlans.Enrollment? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("$profileKey.plan", null) ?: return null
        val days = p.getString("$profileKey.days", "").orEmpty().split(',')
            .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
        val reminder = p.getInt("$profileKey.reminder", -1).takeIf { it >= 0 }
        return TrainingPlans.Enrollment(id, p.getLong("$profileKey.started", 0L), days, reminder)
    }

    fun save(context: Context, profileKey: String, e: TrainingPlans.Enrollment) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("$profileKey.plan", e.planId)
            .putLong("$profileKey.started", e.startedAtMillis)
            .putString("$profileKey.days", e.days.joinToString(",") { it.name })
            .putInt("$profileKey.reminder", e.reminderMinuteOfDay ?: -1)
            .apply()
        PlanReminder.arm(context, e.reminderMinuteOfDay)
    }

    fun clear(context: Context, profileKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("$profileKey.plan").remove("$profileKey.started").remove("$profileKey.days").remove("$profileKey.reminder")
            .apply()
        PlanReminder.arm(context, null)
    }
}

/**
 * The daily reminder: an inexact alarm at the chosen time (no exact-alarm permission needed). When
 * it fires, [PlanReminderReceiver] checks whether today's session is still due and only then posts
 * a notification. Re-armed whenever the app starts, since alarms do not survive a reboot.
 */
object PlanReminder {
    const val CHANNEL_ID = "ofit.plan"
    private const val NOTIFICATION_ID = 4101

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, PlanReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun arm(context: Context, minuteOfDay: Int?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context)
        alarms.cancel(pi)
        if (minuteOfDay == null) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A 15-minute window (no exact-alarm permission needed); a repeating inexact alarm may be
        // held back many hours. The receiver arms the next day's each time it fires.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, 15 * 60_000L, pi)
    }

    /** Post "time for your session" if a session is due today for the current profile. */
    fun remindIfDue(context: Context) {
        val app = context.applicationContext as? TreadmillApplication ?: return
        val controller = app.controller
        val key = controller.profiles.fileName("plan", "json")
        val enrollment = PlanStore.load(context, key) ?: return
        val status = TrainingPlans.status(enrollment, controller.history.entries.value) ?: return
        if (!status.dueToday) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Training plan reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A nudge on your training days when the session is still to do." },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = status.next ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for today's session")
            .setContentText("${next.name} · ${next.totalDurationSeconds / 60} min")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class PlanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching { PlanReminder.remindIfDue(context) }
        // Arm tomorrow's.
        runCatching {
            val app = context.applicationContext as TreadmillApplication
            val e = PlanStore.load(context, app.controller.profiles.fileName("plan", "json"))
            PlanReminder.arm(context, e?.reminderMinuteOfDay)
        }
    }
}
