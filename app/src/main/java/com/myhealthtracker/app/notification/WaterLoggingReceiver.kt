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
        if (intent.action == "com.myhealthtracker.app.ACTION_ADD_WATER") {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            AppContainer.waterRepository.addWater(today, 250)
            Toast.makeText(context, "נוספו 250 מ״ל מים 💧", Toast.LENGTH_SHORT).show()
        }
    }
}
