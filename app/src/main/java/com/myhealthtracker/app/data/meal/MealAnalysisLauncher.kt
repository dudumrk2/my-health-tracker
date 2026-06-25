package com.myhealthtracker.app.data.meal

import android.content.Context
import com.myhealthtracker.app.sync.MealAnalysisScheduler

/** Indirection so the ViewModel can enqueue analysis without depending on WorkManager directly. */
interface MealAnalysisLauncher {
    fun launch(input: MealAnalysisInput)
}

class WorkManagerMealAnalysisLauncher(private val context: Context) : MealAnalysisLauncher {
    override fun launch(input: MealAnalysisInput) = MealAnalysisScheduler.enqueue(context, input)
}
