package com.myhealthtracker.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import com.myhealthtracker.app.ui.meal.MealReminderOverlay

/**
 * Hosts the floating reminder popup in a WindowManager overlay window. Requires
 * SYSTEM_ALERT_WINDOW (also grants the Android 12+ background start exemption). Provides
 * the lifecycle/savedstate/viewmodel owners a ComposeView needs to render off-Activity.
 */
class ReminderOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var overlayView: View? = null
    private val visible = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || overlayView != null) {
            if (overlayView == null) stopSelf()
            return START_NOT_STICKY
        }
        val mealLabel = intent?.getStringExtra(EXTRA_MEAL_LABEL) ?: ""
        val slotIndex = intent?.getIntExtra(EXTRA_SLOT_INDEX, -1) ?: -1
        showOverlay(mealLabel, slotIndex)
        return START_NOT_STICKY
    }

    private fun showOverlay(mealLabel: String, slotIndex: Int) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ReminderOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ReminderOverlayService)
            setViewTreeViewModelStoreOwner(this@ReminderOverlayService)
            setContent {
                MyHealthTrackerTheme {
                    MealReminderOverlay(
                        isVisible = visible.value,
                        title = if (mealLabel.isNotBlank()) "📸 זמן לתעד את $mealLabel" else "היי, לא שכחת משהו?",
                        onLogMeal = { openAddMeal(); dismiss() },
                        onRemindLater = {
                            if (slotIndex >= 0) ReminderScheduler.snooze(applicationContext, slotIndex)
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
        overlayView = composeView
        wm.addView(composeView, params)
        // Move to RESUMED and reveal only after the view attaches to the window,
        // so the ComposeView hosts its composition with the lifecycle transitioning
        // into RESUMED rather than already resumed before attach.
        composeView.post {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            visible.value = true
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

    private fun dismiss() {
        visible.value = false
        // Let the exit animation finish before removing the window.
        overlayView?.postDelayed({ removeAndStop() }, 350)
    }

    private fun removeAndStop() {
        removeView()
        stopSelf()
    }

    private fun removeView() {
        overlayView?.let {
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeView()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    companion object {
        private const val EXTRA_MEAL_LABEL = "meal_label"
        private const val EXTRA_SLOT_INDEX = "slot_index"

        fun start(context: Context, mealLabel: String, slotIndex: Int) {
            context.startService(
                Intent(context, ReminderOverlayService::class.java).apply {
                    putExtra(EXTRA_MEAL_LABEL, mealLabel)
                    putExtra(EXTRA_SLOT_INDEX, slotIndex)
                }
            )
        }
    }
}
