package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.body.FirestoreBodyMeasurementRepository
import com.myhealthtracker.app.data.health.FirestoreHealthRepository
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.insights.FirestoreInsightsRepository
import com.myhealthtracker.app.data.insights.FunctionsInsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.meal.FirestoreMealRepository
import com.myhealthtracker.app.data.meal.FunctionsMealAnalyzer
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.profile.FirestoreProfileRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.water.FirestoreWaterRepository
import com.myhealthtracker.app.data.water.WaterRepository

/**
 * Manual DI graph. Repositories backed by Firestore/Functions are lazy singletons.
 * The meal/water/body/insights repos resolve the current uid internally from
 * [AuthManager]'s underlying FirebaseAuth, so callers don't pass a uid; profile and
 * health repos are uid-parameterized and read [currentUid] from here.
 */
object AppContainer {
    val authManager: AuthManager by lazy { AuthManager() }

    val profileRepository: ProfileRepository by lazy { FirestoreProfileRepository() }
    val healthRepository: HealthRepository by lazy { FirestoreHealthRepository() }
    val mealRepository: MealRepository by lazy { FirestoreMealRepository() }
    val waterRepository: WaterRepository by lazy { FirestoreWaterRepository() }
    val bodyMeasurementRepository: BodyMeasurementRepository by lazy { FirestoreBodyMeasurementRepository() }

    val mealAnalyzer: MealAnalyzer by lazy { FunctionsMealAnalyzer() }

    val insightsRepository: InsightsRepository by lazy { FirestoreInsightsRepository() }
    val insightsRefresher: InsightsRefresher by lazy { FunctionsInsightsRefresher() }

    /** Current authenticated user id, or null when signed out. */
    fun currentUid(): String? = authManager.currentUser?.uid
}
