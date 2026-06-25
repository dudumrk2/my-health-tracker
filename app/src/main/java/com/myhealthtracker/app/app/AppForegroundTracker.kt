package com.myhealthtracker.app.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide "is the UI currently visible" flag, flipped from MainActivity.onStart/onStop.
 * The meal-analysis worker reads it to decide whether to post a completion notification
 * (only when the user is NOT looking at the app).
 */
object AppForegroundTracker {
    private val foreground = AtomicBoolean(false)
    fun onEnterForeground() = foreground.set(true)
    fun onEnterBackground() = foreground.set(false)
    fun isForeground(): Boolean = foreground.get()
}
