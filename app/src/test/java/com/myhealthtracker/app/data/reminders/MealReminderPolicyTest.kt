package com.myhealthtracker.app.data.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealReminderPolicyTest {

    private val settings = ReminderSettings.DEFAULT // 07:00, 12:00, 19:00 all enabled

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `fires when no meals logged`() {
        assertTrue(MealReminderPolicy.shouldRemind(0, settings, emptyList(), t(7)))
    }

    @Test
    fun `breakfast at 8 does not suppress the 12h lunch reminder`() {
        // lunch slot 1: prev enabled 07:00, midpoint 09:30, window [09:30, 12:00]
        assertTrue(MealReminderPolicy.shouldRemind(1, settings, listOf(t(8)), t(12)))
    }

    @Test
    fun `lunch logged at 11h30 suppresses the 12h lunch reminder`() {
        assertFalse(MealReminderPolicy.shouldRemind(1, settings, listOf(t(11, 30)), t(12)))
    }

    @Test
    fun `meal exactly on the midpoint boundary suppresses`() {
        assertFalse(MealReminderPolicy.shouldRemind(1, settings, listOf(t(9, 30)), t(12)))
    }

    @Test
    fun `meal one minute before the midpoint does not suppress`() {
        assertTrue(MealReminderPolicy.shouldRemind(1, settings, listOf(t(9, 29)), t(12)))
    }

    @Test
    fun `first slot window starts at midnight`() {
        // breakfast slot 0: window [00:00, 07:00]; a 06:30 log suppresses
        assertFalse(MealReminderPolicy.shouldRemind(0, settings, listOf(t(6, 30)), t(7)))
    }

    @Test
    fun `disabled slot never fires`() {
        val s = settings.copy(slots = settings.slots.mapIndexed { i, slot ->
            if (i == 1) slot.copy(enabled = false) else slot
        })
        assertFalse(MealReminderPolicy.shouldRemind(1, s, emptyList(), t(12)))
    }

    @Test
    fun `master disabled never fires`() {
        assertFalse(MealReminderPolicy.shouldRemind(0, settings.copy(masterEnabled = false), emptyList(), t(7)))
    }

    @Test
    fun `disabled middle slot widens the next slot window to the previous enabled slot`() {
        // Disable lunch (12:00). Dinner slot 2: prev enabled is breakfast 07:00,
        // midpoint(07:00, 19:00) = 13:00, window [13:00, now].
        val s = settings.copy(slots = settings.slots.mapIndexed { i, slot ->
            if (i == 1) slot.copy(enabled = false) else slot
        })
        // A 12:30 meal is before 13:00 -> does NOT suppress dinner.
        assertTrue(MealReminderPolicy.shouldRemind(2, s, listOf(t(12, 30)), t(19)))
        // A 14:00 meal is inside -> suppresses.
        assertFalse(MealReminderPolicy.shouldRemind(2, s, listOf(t(14)), t(19)))
    }
}
