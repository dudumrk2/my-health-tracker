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
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object QuickActionsNotificationManager {
    private const val CHANNEL_ID = "quick_actions_channel"
    private const val NOTIFICATION_ID = 999
    private var collectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(context: Context) {
        if (collectionJob != null) return
        createChannel(context)

        collectionJob = scope.launch {
            AppContainer.waterRepository.waterLog.collect { waterMap ->
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val waterMl = waterMap[today] ?: 0
                postNotification(context, waterMl)
            }
        }
    }

    fun stop(context: Context) {
        collectionJob?.cancel()
        collectionJob = null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Quick Actions"
            val descriptionText = "Persistent notification for logging meals, workouts, and water"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun postNotification(context: Context, waterMl: Int) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mealIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "add_meal")
        }
        val mealPendingIntent = PendingIntent.getActivity(
            context, 101, mealIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val workoutIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "add_workout")
        }
        val workoutPendingIntent = PendingIntent.getActivity(
            context, 102, workoutIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val waterIntent = Intent(context, WaterLoggingReceiver::class.java).apply {
            action = "com.myhealthtracker.app.ACTION_ADD_WATER"
        }
        val waterPendingIntent = PendingIntent.getBroadcast(
            context, 103, waterIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("מעקב בריאות מהיר")
            .setContentText("שתית היום: $waterMl / 3000 מ״ל 💧")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(appPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "🥗 ארוחה", mealPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "💪 אימון", workoutPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "💧 +250 מ״ל", waterPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
