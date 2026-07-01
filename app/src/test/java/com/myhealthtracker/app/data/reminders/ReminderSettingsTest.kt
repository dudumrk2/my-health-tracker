package com.myhealthtracker.app.data.reminders

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsTest {

    @Test
    fun `default has three enabled meal slots at 7 12 19`() {
        val d = ReminderSettings.DEFAULT
        assertEquals(3, d.slots.size)
        assertEquals(LocalTime.of(7, 0), d.slots[0].time)
        assertEquals(LocalTime.of(12, 0), d.slots[1].time)
        assertEquals(LocalTime.of(19, 0), d.slots[2].time)
        assertEquals(listOf("ארוחת בוקר", "ארוחת צהריים", "ארוחת ערב"), d.slots.map { it.mealLabel })
        assertEquals(true, d.masterEnabled)
    }

    @Test
    fun `codec round-trips slots including a disabled one`() {
        val slots = listOf(
            ReminderSlot(LocalTime.of(9, 30), "ארוחת בוקר", true),
            ReminderSlot(LocalTime.of(13, 0), "ארוחת צהריים", false),
        )
        val decoded = ReminderSettingsCodec.decodeSlots(ReminderSettingsCodec.encodeSlots(slots))
        assertEquals(slots, decoded)
    }

    @Test
    fun `in-memory store returns updated settings`() = runTest {
        val store = InMemoryReminderSettingsStore()
        val updated = ReminderSettings.DEFAULT.copy(masterEnabled = false)
        store.update(updated)
        assertEquals(updated, store.settings.first())
    }
}
