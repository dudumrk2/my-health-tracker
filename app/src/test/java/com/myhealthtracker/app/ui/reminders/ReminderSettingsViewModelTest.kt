package com.myhealthtracker.app.ui.reminders

import com.myhealthtracker.app.data.reminders.InMemoryReminderSettingsStore
import com.myhealthtracker.app.data.reminders.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `setSlotTime persists and reschedules`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        var rescheduledWith: ReminderSettings? = null
        val vm = ReminderSettingsViewModel(store) { rescheduledWith = it }

        vm.setSlotTime(1, LocalTime.of(13, 0))
        testScheduler.advanceUntilIdle()

        assertEquals(LocalTime.of(13, 0), store.settings.first().slots[1].time)
        assertEquals(LocalTime.of(13, 0), rescheduledWith?.slots?.get(1)?.time)
    }

    @Test
    fun `setMasterEnabled false persists`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        val vm = ReminderSettingsViewModel(store) {}

        vm.setMasterEnabled(false)
        testScheduler.advanceUntilIdle()

        assertFalse(store.settings.first().masterEnabled)
    }

    @Test
    fun `setSlotEnabled toggles the target slot only`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        val vm = ReminderSettingsViewModel(store) {}

        vm.setSlotEnabled(0, false)
        testScheduler.advanceUntilIdle()

        val slots = store.settings.first().slots
        assertFalse(slots[0].enabled)
        assertTrue(slots[1].enabled)
    }
}
