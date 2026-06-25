package com.myhealthtracker.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.myhealthtracker.app.app.AppForegroundTracker
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import com.myhealthtracker.app.data.meal.MealAnalysisRunner
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.MealAnalysisNotifier
import com.myhealthtracker.app.util.MealImageStore

class MealAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val input = fromData(inputData) ?: return Result.success()
        val runner = MealAnalysisRunner(
            analyzer = AppContainer.mealAnalyzer,
            repository = AppContainer.mealRepository,
            imageToBase64 = { path -> MealImageStore.readAsBase64(path) }
        )
        val result = runner.run(input, runAttemptCount, MealAnalysisRunner.MAX_ATTEMPTS)
        return when (result.outcome) {
            MealAnalysisRunner.Outcome.SUCCESS -> {
                if (!AppForegroundTracker.isForeground())
                    MealAnalysisNotifier.notifySuccess(applicationContext, input.mealId, result.calories ?: 0)
                Result.success()
            }
            MealAnalysisRunner.Outcome.RETRY -> Result.retry()
            MealAnalysisRunner.Outcome.FAILED -> {
                if (!AppForegroundTracker.isForeground())
                    MealAnalysisNotifier.notifyFailure(applicationContext, input.mealId)
                Result.success()
            }
        }
    }

    companion object {
        private const val KEY_MEAL_ID = "mealId"
        private const val KEY_INPUT_TYPE = "inputType"
        private const val KEY_TEXT = "text"
        private const val KEY_IMAGE_PATH = "imagePath"
        private const val KEY_DATE = "date"

        fun toData(input: MealAnalysisInput): Data = Data.Builder()
            .putString(KEY_MEAL_ID, input.mealId)
            .putString(KEY_INPUT_TYPE, input.inputType)
            .putString(KEY_TEXT, input.text)
            .putString(KEY_IMAGE_PATH, input.localImagePath)
            .putString(KEY_DATE, input.date)
            .build()

        fun fromData(data: Data): MealAnalysisInput? {
            val mealId = data.getString(KEY_MEAL_ID) ?: return null
            val inputType = data.getString(KEY_INPUT_TYPE) ?: return null
            val date = data.getString(KEY_DATE) ?: return null
            return MealAnalysisInput(mealId, inputType, data.getString(KEY_TEXT), data.getString(KEY_IMAGE_PATH), date)
        }
    }
}
