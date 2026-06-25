package com.myhealthtracker.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import java.util.concurrent.TimeUnit

/** Enqueues a unique meal-analysis job per mealId. REPLACE lets a manual retry re-run it. */
object MealAnalysisScheduler {
    fun workName(mealId: String) = "mealAnalysis_$mealId"

    fun enqueue(context: Context, input: MealAnalysisInput) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<MealAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(MealAnalysisWorker.toData(input))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(input.mealId), ExistingWorkPolicy.REPLACE, request)
    }
}
