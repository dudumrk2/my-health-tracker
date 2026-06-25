package com.myhealthtracker.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.R

/** One-shot notifications fired by MealAnalysisWorker when the app is in the background. */
object MealAnalysisNotifier {
    private const val CHANNEL_ID = "meal_analysis_channel"
    private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun notifySuccess(context: Context, mealId: String, calories: Int) =
        post(context, mealId, "המנה נותחה ✓", "$calories קלוריות — הקש לצפייה ועריכה")

    fun notifyFailure(context: Context, mealId: String) =
        post(context, mealId, "ניתוח המנה נכשל", "הקש כדי לנסות שוב או להזין ידנית")

    private fun post(context: Context, mealId: String, title: String, body: String) {
        val appContext = context.applicationContext
        createChannel(appContext)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(QuickActionsNotificationManager.EXTRA_NAVIGATE_TO, QuickActionsNotificationManager.DEST_MEAL_RESULT)
            putExtra(QuickActionsNotificationManager.EXTRA_MEAL_ID, mealId)
        }
        val pending = PendingIntent.getActivity(appContext, mealId.hashCode(), intent, PENDING_FLAGS)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(mealId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Meal analysis", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifies when a meal photo finishes analysis" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
