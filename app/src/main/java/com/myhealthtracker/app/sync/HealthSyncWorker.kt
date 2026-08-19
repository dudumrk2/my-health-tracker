package com.myhealthtracker.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.data.health.HealthConnectManager
import com.myhealthtracker.app.data.health.FirestoreHealthRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val authManager = AuthManager()
    private val healthRepository = FirestoreHealthRepository()
    private val healthConnectManager = HealthConnectManager(context)

    override suspend fun doWork(): Result {
        val uid = authManager.currentUser?.uid ?: return Result.success()

        Log.d("HealthSyncWorker", "Starting background health sync for user: $uid")

        if (!healthConnectManager.isSdkAvailable()) {
            Log.w("HealthSyncWorker", "Health Connect SDK is not available")
            return Result.success()
        }

        if (!healthConnectManager.hasAllPermissions()) {
            Log.w("HealthSyncWorker", "Health Connect permissions are missing")
            return Result.success()
        }

        return try {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            
            // Sync last 7 days to ensure gaps are filled if app wasn't running
            for (i in 0 until 7) {
                val date = today.minusDays(i.toLong())
                val startOfDay = date.atStartOfDay(zoneId).toInstant()
                val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()

                val steps = healthConnectManager.readDailySteps(startOfDay, endOfDay)
                val sleep = healthConnectManager.readSleepSessions(startOfDay, endOfDay)
                val workouts = healthConnectManager.readExerciseSessions(startOfDay, endOfDay)

                val mapped = healthRepository.mapHealthConnectData(steps, sleep, workouts)
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

                healthRepository.saveDailyHealthData(
                    uid = uid,
                    date = dateStr,
                    steps = steps,
                    sleepSessions = mapped.sleepSessions,
                    workouts = mapped.workouts
                ).first()
                
                Log.d("HealthSyncWorker", "Synced health data for $dateStr")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("HealthSyncWorker", "Error syncing health data", e)
            Result.retry()
        }
    }
}
