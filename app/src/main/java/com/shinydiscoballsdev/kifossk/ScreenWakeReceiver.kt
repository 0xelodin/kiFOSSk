package com.shinydiscoballsdev.kifossk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.util.Calendar

/**
 * Turns the screen on at the scheduled wake time (ScreenSchedule) and
 * arms the alarm for the next one.
 *
 * The kiosk is the home app, so with the screen on it is what shows; its
 * onResume() then applies the keep-on period that usually starts with the
 * wake time. The wake lock only bridges the moment until the window's own
 * keep-on flag takes over.
 *
 * Android 12+: exact alarms need "Alarms & reminders" access; without it the
 * alarm is inexact (still on time while charging, which kiosks usually are).
 * Android 14+ may additionally require the "turn screen on" permission for
 * ACQUIRE_CAUSES_WAKEUP.
 */
class ScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.i("KioskSchedule", "wake alarm fired")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "kiFOSSk:scheduledWake"
        ).acquire(WAKE_LOCK_MS)

        schedule(context)
    }

    companion object {
        private const val WAKE_LOCK_MS = 10_000L

        /** (Re)arm the next wake alarm; cancels it when the schedule has none. */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ScreenWakeReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)

            if (!KioskPrefs.isScheduleEnabled(context)) return
            val next = ScreenSchedule.nextWake(context, Calendar.getInstance()) ?: return

            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
            android.util.Log.i("KioskSchedule", "next wake at ${java.util.Date(next)} (exact=$exactAllowed)")
        }
    }
}
