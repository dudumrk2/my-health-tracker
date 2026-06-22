package com.myhealthtracker.app.data.celebration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Single source of celebration events. The overlay collects [events]; producers call
 * [tryCelebrate] (state-derived, deduped) or [celebrateNow] (one-shot actions that
 * need no dedup). Fire-and-forget: callers never block.
 */
class CelebrationController(
    private val store: CelebrationStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    // A BUFFERED channel rather than a replay=0 SharedFlow: events produced before the
    // overlay subscribes (e.g. a step goal already met on the first dashboard emission at
    // launch) are buffered and delivered when it attaches, instead of being silently
    // dropped after their dedup key was already persisted. Each event is received exactly
    // once, so it is not re-shown after a configuration-change recreation.
    private val _events = Channel<CelebrationEvent>(Channel.BUFFERED)
    val events: Flow<CelebrationEvent> = _events.receiveAsFlow()

    // Keys already handled this process-session. Checked-and-added synchronously before
    // launching: this closes the read-then-write race (two near-simultaneous calls for the
    // same key can't both pass the persistent check) and avoids redundant DataStore reads
    // under the per-emission firing pattern in DashboardViewModel.
    private val handledKeys = Collections.synchronizedSet(mutableSetOf<String>())

    /** Fires once per [CelebrationEvent.dedupKey]; subsequent calls are suppressed. */
    fun tryCelebrate(event: CelebrationEvent?) {
        if (event == null) return
        if (!handledKeys.add(event.dedupKey)) return
        scope.launch {
            try {
                if (!store.hasCelebrated(event.dedupKey)) {
                    store.markCelebrated(event.dedupKey)
                    _events.send(event)
                }
            } catch (_: Throwable) {
                // Persistent check/mark failed; drop the guard so a later emission can retry.
                handledKeys.remove(event.dedupKey)
            }
        }
    }

    fun tryCelebrate(events: List<CelebrationEvent>) = events.forEach { tryCelebrate(it) }

    /** Always emits — for inherently one-shot triggers (e.g. saving a meal). */
    fun celebrateNow(event: CelebrationEvent?) {
        if (event == null) return
        scope.launch { _events.send(event) }
    }
}
