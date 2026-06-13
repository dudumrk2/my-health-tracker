package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.insights.FirestoreInsightsRepository
import com.myhealthtracker.app.data.insights.FunctionsInsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.meal.FirestoreMealRepository
import com.myhealthtracker.app.data.meal.FunctionsMealAnalyzer
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.FirestoreWaterRepository
import com.myhealthtracker.app.data.water.WaterRepository

object AppContainer {
    val mealRepository: MealRepository by lazy { FirestoreMealRepository() }
    val waterRepository: WaterRepository by lazy { FirestoreWaterRepository() }
    val mealAnalyzer: MealAnalyzer by lazy { FunctionsMealAnalyzer() }
    val insightsRepository: InsightsRepository by lazy { FirestoreInsightsRepository() }
    val insightsRefresher: InsightsRefresher by lazy { FunctionsInsightsRefresher() }
}
