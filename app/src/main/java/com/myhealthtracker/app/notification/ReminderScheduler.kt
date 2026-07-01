package com.myhealthtracker.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myhealthtracker.app.data.reminders.ReminderSettings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules one inexact daily alarm per enabled meal slot. Styled after HealthSyncScheduler.
 * Inexact (setAndAllowWhileIdle) so no SCHEDULE_EXACT_ALARM permission is needed.
 */
object ReminderScheduler {
    const val EXTRA_SLOT_INDEX = "slot_index"
    private const val ACTION = "com.myhealthtracker.app.MEAL_REMINDER"
    private const val REQUEST_BASE = 7000
    private const val MAX_SLOTS = 8

    fun armAll(context: Context, settings: ReminderSettings) {
        if (!settings.masterEnabled) { cancelAll(context); return }
        settings.slots.forEachIndexed { index, slot ->
            if (slot.enabled) armNextOccurrence(context, index, slot.time)
            else cancelSlot(context, index)
        }
    }

    fun armNextOccurrence(context: Context, slotIndex: Int, time: LocalTime) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, slotIndex, triggerAt)
    }

    fun snooze(context: Context, slotIndex: Int, minutes: Long = 30) {
        setAlarm(context, slotIndex, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun cancelAll(context: Context) {
        for (i in 0 until MAX_SLOTS) cancelSlot(context, i)
    }

    private fun setAlarm(context: Context, slotIndex: Int, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, slotIndex))
    }

    private fun cancelSlot(context: Context, slotIndex: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, slotIndex))
    }

    private fun pendingIntent(context: Context, slotIndex: Int): PendingIntent {
        val intent = Intent(context.applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_BASE + slotIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
