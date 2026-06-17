package com.myhealthtracker.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.R
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object QuickActionsNotificationManager {
    // Shared keys/values used by the notification actions and their handlers
    // (Navigation deep links and WaterLoggingReceiver), kept in one place.
    const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"
    const val DEST_ADD_MEAL = "add_meal"
    const val DEST_ADD_WORKOUT = "add_workout"
    const val ACTION_ADD_WATER = "com.myhealthtracker.app.ACTION_ADD_WATER"
    const val WATER_STEP_ML = 250

    private const val CHANNEL_ID = "quick_actions_channel"
    private const val NOTIFICATION_ID = 999
    private const val PENDING_FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private var collectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(context: Context) {
        // This object is a process-lived singleton, so it must never hold an Activity
        // context — that would leak the Activity across every recreation. Bind to the
        // application context for the channel, the collector, and every posted notification.
        val appContext = context.applicationContext
        if (collectionJob != null) return
        createChannel(appContext)

        collectionJob = scope.launch {
            val uid = AppContainer.currentUid()
            // Daily water goal (honors the user's manual override), with a safe generic
            // fallback when the profile is missing or the user is signed out.
            val targetFlow = if (uid != null) {
                AppContainer.profileRepository.getUserProfile(uid)
                    .map { GoalCalculator.compute(it.getOrNull() ?: UserProfile()).waterMl }
            } else {
                flowOf(GoalCalculator.compute(UserProfile()).waterMl)
            }
            combine(AppContainer.waterRepository.waterLog, targetFlow) { waterMap, targetMl ->
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                (waterMap[today] ?: 0) to targetMl
            }.collect { (waterMl, targetMl) ->
                postNotification(appContext, waterMl, targetMl)
            }
        }
    }

    fun stop(context: Context) {
        collectionJob?.cancel()
        collectionJob = null
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private fun postNotification(context: Context, waterMl: Int, targetMl: Int) {
        val appPendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent(context, null), PENDING_FLAGS
        )
        val mealPendingIntent = PendingIntent.getActivity(
            context, 101, launchIntent(context, DEST_ADD_MEAL), PENDING_FLAGS
        )
        val workoutPendingIntent = PendingIntent.getActivity(
            context, 102, launchIntent(context, DEST_ADD_WORKOUT), PENDING_FLAGS
        )

        val waterIntent = Intent(context, WaterLoggingReceiver::class.java).apply {
            action = ACTION_ADD_WATER
        }
        val waterPendingIntent = PendingIntent.getBroadcast(
            context, 103, waterIntent, PENDING_FLAGS
        )

        // Custom layout so each action is a readable two-line button (emoji over label)
        // instead of a single, system-truncated action row. The water cell shows only an
        // enlarged drop; the daily-water progress sits above the buttons.
        val content = RemoteViews(context.packageName, R.layout.notification_quick_actions).apply {
            setTextViewText(R.id.notification_water_status, "שתית היום: $waterMl / $targetMl מ״ל 💧")
            setOnClickPendingIntent(R.id.action_meal, mealPendingIntent)
            setOnClickPendingIntent(R.id.action_workout, workoutPendingIntent)
            setOnClickPendingIntent(R.id.action_water, waterPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(appPendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Intent that opens [MainActivity], optionally carrying a deep-link [destination]. */
    private fun launchIntent(context: Context, destination: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (destination != null) putExtra(EXTRA_NAVIGATE_TO, destination)
        }
}
