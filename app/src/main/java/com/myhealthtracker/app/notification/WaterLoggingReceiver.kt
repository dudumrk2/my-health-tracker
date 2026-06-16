package com.myhealthtracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.myhealthtracker.app.di.AppContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WaterLoggingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuickActionsNotificationManager.ACTION_ADD_WATER) return

        // The receiver can fire in a fresh process (e.g. after the app was killed). If
        // there is no signed-in user, addWater() silently no-ops, so don't claim success.
        if (AppContainer.currentUid() == null) {
            Toast.makeText(context, "יש להתחבר כדי לרשום מים", Toast.LENGTH_SHORT).show()
            return
        }

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        AppContainer.waterRepository.addWater(today, QuickActionsNotificationManager.WATER_STEP_ML)
        Toast.makeText(
            context,
            "נוספו ${QuickActionsNotificationManager.WATER_STEP_ML} מ״ל מים 💧",
            Toast.LENGTH_SHORT
        ).show()
    }
}
