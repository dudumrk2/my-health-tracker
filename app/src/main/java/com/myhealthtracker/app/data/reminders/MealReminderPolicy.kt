package com.myhealthtracker.app.data.reminders

import java.time.LocalTime

/**
 * Decides whether the meal-time reminder for a slot should fire. The reminder prompts the
 * user to photograph the meal, so it fires UNLESS that meal was already logged. "Already
 * logged" = a complete meal in the suppression window [Sᵢ, now], where Sᵢ is the midpoint
 * between the previous enabled slot and this slot (midnight for the first slot). The
 * midpoint boundary keeps meals independent: logging breakfast does not suppress lunch.
 */
object MealReminderPolicy {

    fun shouldRemind(
        slotIndex: Int,
        settings: ReminderSettings,
        todaysMealTimes: List<LocalTime>,
        now: LocalTime
    ): Boolean {
        if (!settings.masterEnabled) return false
        val slot = settings.slots.getOrNull(slotIndex) ?: return false
        if (!slot.enabled) return false

        val prevEnabled = settings.slots.take(slotIndex).lastOrNull { it.enabled }
        val lowerBound =
            if (prevEnabled == null) LocalTime.MIDNIGHT
            else midpoint(prevEnabled.time, slot.time)

        val alreadyLogged = todaysMealTimes.any { it >= lowerBound && it <= now }
        return !alreadyLogged
    }

    private fun midpoint(a: LocalTime, b: LocalTime): LocalTime {
        val mid = (a.toSecondOfDay() + b.toSecondOfDay()) / 2
        return LocalTime.ofSecondOfDay(mid.toLong())
    }
}
