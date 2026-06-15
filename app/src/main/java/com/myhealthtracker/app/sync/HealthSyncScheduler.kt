package com.myhealthtracker.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues the Health Connect -> Firestore sync. The periodic job uses the unique name
 * [PERIODIC_WORK_NAME] (the same name [com.myhealthtracker.app.ui.auth.AuthViewModel]
 * cancels on sign-out). [syncNow] runs an immediate one-off sync, e.g. right after
 * permissions are granted, so the UI reflects today's data without waiting 6 hours.
 */
object HealthSyncScheduler {
    const val PERIODIC_WORK_NAME = "HealthConnectSyncWork"
    private const val IMMEDIATE_WORK_NAME = "HealthConnectSyncWorkNow"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<HealthSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
