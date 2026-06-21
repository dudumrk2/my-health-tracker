package com.myhealthtracker.app.data.celebration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CelebrationControllerTest {

    private val event = CelebrationEvent(CelebrationType.STEP_GOAL, "steps-2026-06-21")

    @Test
    fun `tryCelebrate emits once and suppresses duplicate keys`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.tryCelebrate(event)
        advanceUntilIdle()
        controller.tryCelebrate(event) // same dedup key
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(CelebrationType.STEP_GOAL, received.first().type)
    }

    @Test
    fun `tryCelebrate ignores null`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.tryCelebrate(null as CelebrationEvent?)
        advanceUntilIdle()

        assertEquals(0, received.size)
    }

    @Test
    fun `celebrateNow emits every time regardless of dedup`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.celebrateNow(event)
        advanceUntilIdle()
        runCurrent() // flush background collector loop-back
        controller.celebrateNow(event)
        advanceUntilIdle()
        runCurrent() // flush background collector loop-back

        assertEquals(2, received.size)
    }
}
