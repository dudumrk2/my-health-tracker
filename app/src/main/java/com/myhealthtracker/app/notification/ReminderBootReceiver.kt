package com.myhealthtracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms all reminder alarms after a device reboot (alarms don't survive reboot). */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppContainer.reminderSettingsStore.settings.first()
                ReminderScheduler.armAll(appContext, settings)
            } finally {
                pending.finish()
            }
        }
    }
}
