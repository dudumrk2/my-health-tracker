package com.myhealthtracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.reminders.MealReminderPolicy
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fired at a slot's meal time. Skips during a call, reads today's meals, asks
 * MealReminderPolicy whether the meal is still un-logged, and if so shows the reminder.
 * Always re-arms the next daily occurrence so the schedule keeps running.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotIndex = intent.getIntExtra(ReminderScheduler.EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppContainer.reminderSettingsStore.settings.first()
                val slot = settings.slots.getOrNull(slotIndex)

                // Re-arm the next daily occurrence regardless of the outcome below.
                if (slot != null && slot.enabled && settings.masterEnabled) {
                    ReminderScheduler.armNextOccurrence(appContext, slotIndex, slot.time)
                }

                if (slot == null || AppContainer.currentUid() == null) return@launch
                if (!Settings.canDrawOverlays(appContext)) return@launch

                // Call guard: never cover the call/answer UI. Snooze a few minutes instead.
                val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audio.mode == AudioManager.MODE_IN_CALL ||
                    audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
                    audio.mode == AudioManager.MODE_RINGTONE
                ) {
                    ReminderScheduler.snooze(appContext, slotIndex, 5)
                    return@launch
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                // meals is a StateFlow seeded empty and loaded async; wait briefly for the
                // Firestore (offline-cached) load. A genuinely zero-meal day times out and
                // we proceed with an empty list (which correctly triggers a reminder).
                val meals = withTimeoutOrNull(4000) {
                    AppContainer.mealRepository.meals.first { it.isNotEmpty() }
                } ?: emptyList()

                val zone = ZoneId.systemDefault()
                val mealTimes = meals
                    .filter { it.date == today && it.status == MealStatus.COMPLETE }
                    .map { it.loggedAt.atZone(zone).toLocalTime() }

                if (MealReminderPolicy.shouldRemind(slotIndex, settings, mealTimes, LocalTime.now())) {
                    ReminderOverlayService.start(appContext, slot.mealLabel, slotIndex)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
