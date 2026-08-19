package com.myhealthtracker.app.notification

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import com.myhealthtracker.app.ui.meal.MealReminderOverlay

/**
 * Shows the floating reminder popup as a translucent, borderless Activity. Launched from
 * [ReminderAlarmReceiver] — starting an Activity from the background is permitted because
 * the app holds SYSTEM_ALERT_WINDOW (the background-activity-launch exemption), which a
 * plain `startService` does NOT get. Deliberately does NOT set the show-when-locked /
 * turn-screen-on flags: it never wakes the screen or shows over a secure lock screen; it
 * simply is there once the user turns the screen on / unlocks, and floats over whatever
 * app is in the foreground.
 */
class ReminderActivity : ComponentActivity() {

    private val visible = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No system activity transition — only the Compose slide-in animates.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val mealLabel = intent?.getStringExtra(EXTRA_MEAL_LABEL) ?: ""
        val slotIndex = intent?.getIntExtra(EXTRA_SLOT_INDEX, -1) ?: -1
        val soundEnabled = intent?.getBooleanExtra(EXTRA_SOUND_ENABLED, true) ?: true

        if (soundEnabled) playNotificationSound()

        setContent {
            MyHealthTrackerTheme {
                MealReminderOverlay(
                    isVisible = visible.value,
                    title = if (mealLabel.isNotBlank()) getString(com.myhealthtracker.app.R.string.reminder_time_to_log, mealLabel) else getString(com.myhealthtracker.app.R.string.reminder_default_title),
                    onLogMeal = { openAddMeal(); dismiss() },
                    onRemindLater = {
                        if (slotIndex >= 0) ReminderScheduler.snooze(applicationContext, slotIndex)
                        dismiss()
                    },
                    onDismiss = { dismiss() }
                )
            }
            // Flip visible after first composition so AnimatedVisibility plays the slide-in.
            LaunchedEffect(Unit) { visible.value = true }
        }
    }

    /** Plays the device's default notification sound; respects silent/vibrate. */
    private fun playNotificationSound() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        }
    }

    private fun openAddMeal() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                QuickActionsNotificationManager.EXTRA_NAVIGATE_TO,
                QuickActionsNotificationManager.DEST_ADD_MEAL
            )
        })
    }

    /** Play the exit animation, then finish without a system transition. */
    private fun dismiss() {
        visible.value = false
        window.decorView.postDelayed({
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 350)
    }

    companion object {
        private const val EXTRA_MEAL_LABEL = "meal_label"
        private const val EXTRA_SLOT_INDEX = "slot_index"
        private const val EXTRA_SOUND_ENABLED = "sound_enabled"

        fun start(context: Context, mealLabel: String, slotIndex: Int, soundEnabled: Boolean) {
            context.startActivity(
                Intent(context, ReminderActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                    putExtra(EXTRA_MEAL_LABEL, mealLabel)
                    putExtra(EXTRA_SLOT_INDEX, slotIndex)
                    putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                }
            )
        }
    }
}
