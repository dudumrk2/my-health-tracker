package com.myhealthtracker.app.di

import android.content.Context
import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.account.FunctionsAccountRepository
import com.myhealthtracker.app.data.activity.ActivityRepository
import com.myhealthtracker.app.data.activity.FirestoreActivityRepository
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
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.DataStoreCelebrationStore
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import com.myhealthtracker.app.data.reminders.DataStoreReminderSettingsStore
import com.myhealthtracker.app.data.reminders.InMemoryReminderSettingsStore
import com.myhealthtracker.app.data.reminders.ReminderSettingsStore
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

    val accountRepository: AccountRepository by lazy { FunctionsAccountRepository() }
    val activityRepository: ActivityRepository by lazy { FirestoreActivityRepository() }

    // Celebrations. Backed by an in-memory store until initCelebrations() swaps in
    // the DataStore-backed one (called from MyHealthApp with an app Context).
    @Volatile
    private var _celebrationController: CelebrationController? = null

    val celebrationController: CelebrationController
        get() = _celebrationController
            ?: CelebrationController(InMemoryCelebrationStore()).also { _celebrationController = it }

    fun initCelebrations(context: Context) {
        _celebrationController = CelebrationController(DataStoreCelebrationStore(context.applicationContext))
    }

    // Meal reminders. Device-local settings; swapped to DataStore-backed in init().
    @Volatile
    private var _reminderSettingsStore: ReminderSettingsStore? = null

    val reminderSettingsStore: ReminderSettingsStore
        get() = _reminderSettingsStore
            ?: InMemoryReminderSettingsStore().also { _reminderSettingsStore = it }

    @Volatile private var appContext: android.content.Context? = null

    /** Call once from Application.onCreate. Stores the app context and inits celebrations. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        initCelebrations(context)
        _reminderSettingsStore = DataStoreReminderSettingsStore(context.applicationContext)
    }

    val mealAnalysisLauncher: com.myhealthtracker.app.data.meal.MealAnalysisLauncher by lazy {
        val ctx = appContext ?: error("AppContainer.init(context) must be called before mealAnalysisLauncher")
        com.myhealthtracker.app.data.meal.WorkManagerMealAnalysisLauncher(ctx)
    }

    /** Current authenticated user id, or null when signed out. */
    fun currentUid(): String? = authManager.currentUser?.uid
}
