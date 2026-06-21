package com.myhealthtracker.app.data.celebration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Single source of celebration events. The overlay collects [events]; producers call
 * [tryCelebrate] (state-derived, deduped via [store]) or [celebrateNow] (one-shot
 * actions that need no dedup). Fire-and-forget: callers never block.
 */
class CelebrationController(
    private val store: CelebrationStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _events = MutableSharedFlow<CelebrationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CelebrationEvent> = _events.asSharedFlow()

    /** Fires once per [CelebrationEvent.dedupKey]; subsequent calls are suppressed. */
    fun tryCelebrate(event: CelebrationEvent?) {
        if (event == null) return
        scope.launch {
            if (!store.hasCelebrated(event.dedupKey)) {
                store.markCelebrated(event.dedupKey)
                _events.emit(event)
            }
        }
    }

    fun tryCelebrate(events: List<CelebrationEvent>) = events.forEach { tryCelebrate(it) }

    /** Always emits — for inherently one-shot triggers (e.g. saving a meal). */
    fun celebrateNow(event: CelebrationEvent?) {
        if (event == null) return
        scope.launch { _events.emit(event) }
    }
}
