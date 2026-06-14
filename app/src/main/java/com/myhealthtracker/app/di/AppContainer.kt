package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.meal.FunctionsMealAnalyzer
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.WaterRepository

object AppContainer {
    val mealRepository: MealRepository = FakeRepository
    val waterRepository: WaterRepository = FakeRepository
    val mealAnalyzer: MealAnalyzer by lazy { FunctionsMealAnalyzer() }
}
