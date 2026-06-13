package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.WaterRepository

object AppContainer {
    // Swapped to Firestore-backed implementations in Task 11.
    var mealRepository: MealRepository = FakeRepository
        internal set
    var waterRepository: WaterRepository = FakeRepository
        internal set
}
