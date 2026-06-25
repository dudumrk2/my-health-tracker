package com.myhealthtracker.app.data

import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.data.water.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object FakeRepository : ProfileRepository, HealthRepository, MealRepository, WaterRepository, BodyMeasurementRepository {
    // Current user auth state (mock)
    private val _isUserLoggedIn = MutableStateFlow(true)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    // Profile state (using English gender value "male")
    private val _profile = MutableStateFlow<UserProfile?>(
        UserProfile(
            birthYear = 1995,
            weightKg = 75.0,
            heightCm = 178.0,
            gender = "male",
            themePreference = "system",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    )
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    // Daily health data (Steps, Sleep, Workouts) keyed by date "yyyy-MM-dd"
    private val _healthDaily = MutableStateFlow<Map<String, DailyHealthData>>(emptyMap())
    val healthDaily: StateFlow<Map<String, DailyHealthData>> = _healthDaily.asStateFlow()

    // Meals list
    private val _meals = MutableStateFlow<List<MealEntry>>(emptyList())
    override val meals: StateFlow<List<MealEntry>> = _meals.asStateFlow()

    // Body measurements list
    private val _bodyMeasurements = MutableStateFlow<List<BodyMeasurement>>(emptyList())
    override val bodyMeasurements: StateFlow<List<BodyMeasurement>> = _bodyMeasurements.asStateFlow()

    // Water log keyed by date -> ml
    private val _waterLog = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val waterLog: StateFlow<Map<String, Int>> = _waterLog.asStateFlow()

    // AI Unified insights keyed by date -> (today insight, tomorrow insight)
    private val _aiInsights = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val aiInsights: StateFlow<Map<String, Pair<String, String>>> = _aiInsights.asStateFlow()

    init {
        setupMockData()
    }

    fun resetToDefaults() {
        _isUserLoggedIn.value = true
        _profile.value = UserProfile(
            birthYear = 1995,
            weightKg = 75.0,
            heightCm = 178.0,
            gender = "male",
            themePreference = "system",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        setupMockData()
    }

    fun login() {
        _isUserLoggedIn.value = true
    }

    fun logout() {
        _isUserLoggedIn.value = false
    }

    // --- ProfileRepository Implementation ---

    override fun getUserProfile(uid: String): Flow<Result<UserProfile?>> = _profile.map { Result.success(it) }

    override fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>> = callbackFlow {
        val validation = validateProfile(profile)
        if (validation.isFailure) {
            trySend(Result.failure(validation.exceptionOrNull() ?: Exception("Validation failed")))
            close()
            return@callbackFlow
        }
        _profile.value = profile.copy(updatedAt = Instant.now())
        trySend(Result.success(Unit))
        close()
        awaitClose()
    }

    override fun validateProfile(profile: UserProfile): Result<Unit> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (profile.birthYear < 1900 || profile.birthYear > currentYear) {
            return Result.failure(IllegalArgumentException("Birth year must be between 1900 and $currentYear"))
        }
        if (profile.gender.isEmpty()) {
            return Result.failure(IllegalArgumentException("Gender is required"))
        }
        if (profile.gender != "male" && profile.gender != "female") {
            return Result.failure(IllegalArgumentException("Gender must be 'male' or 'female'"))
        }
        if (profile.weightKg < 30.0 || profile.weightKg > 300.0) {
            return Result.failure(IllegalArgumentException("Weight must be between 30.0 kg and 300.0 kg"))
        }
        if (profile.heightCm < 100.0 || profile.heightCm > 250.0) {
            return Result.failure(IllegalArgumentException("Height must be between 100.0 cm and 250.0 cm"))
        }
        return Result.success(Unit)
    }

    override fun calculateAge(birthYear: Int, currentYear: Int): Int {
        if (birthYear <= 0) return 0
        val age = currentYear - birthYear
        return if (age >= 0) age else 0
    }

    // Helper for direct VM/Mock calls
    fun saveProfile(birthYear: Int, weightKg: Double, heightCm: Double, gender: String, themePreference: String = "system") {
        _profile.value = UserProfile(
            birthYear = birthYear,
            weightKg = weightKg,
            heightCm = heightCm,
            gender = gender,
            themePreference = themePreference,
            createdAt = _profile.value?.createdAt ?: Instant.now(),
            updatedAt = Instant.now()
        )
    }

    // --- HealthRepository Implementation ---
    
    override fun getWeeklyHealthData(uid: String, startDate: String, endDate: String): Flow<Result<List<DailyHealthData>>> = _healthDaily.map { map ->
        val list = map.values.filter { it.date in startDate..endDate }
        Result.success(list)
    }

    override fun getDailyHealthData(uid: String, date: String): Flow<Result<DailyHealthData?>> = _healthDaily.map {
        Result.success(it[date] ?: DailyHealthData(date = date, steps = 0, sleepMinutes = 0))
    }

    override fun saveDailyHealthData(
        uid: String,
        date: String,
        steps: Long,
        sleepSessions: List<SleepSessionInfo>,
        workouts: List<ExerciseSessionInfo>
    ): Flow<Result<Unit>> = callbackFlow {
        saveDailyHealthData(date, steps, aggregateSleepMinutes(sleepSessions), sleepSessions, workouts)
        trySend(Result.success(Unit))
        close()
        awaitClose()
    }

    override fun saveManualWorkout(
        uid: String,
        date: String,
        type: String,
        durationMin: Int,
        startTime: Instant
    ): Flow<Result<Unit>> = callbackFlow {
        addWorkout(date, type, durationMin, startTime)
        trySend(Result.success(Unit))
        close()
        awaitClose()
    }

    private fun aggregateSleepMinutes(sessions: List<SleepSessionInfo>): Int {
        if (sessions.isEmpty()) return 0
        val sorted = sessions.sortedBy { it.start.toEpochMilli() }
        val merged = mutableListOf<SleepSessionInfo>()
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start.toEpochMilli() <= current.end.toEpochMilli()) {
                if (next.end.toEpochMilli() > current.end.toEpochMilli()) {
                    current = SleepSessionInfo(current.start, next.end)
                }
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        var totalMin = 0L
        for (m in merged) {
            val diffMs = m.end.toEpochMilli() - m.start.toEpochMilli()
            totalMin += diffMs / (1000 * 60)
        }
        return totalMin.toInt()
    }

    // Helpers for direct VM/Mock calls
    fun getDailyHealthData(date: String): DailyHealthData {
        return _healthDaily.value[date] ?: DailyHealthData(date = date, steps = 0, sleepMinutes = 0)
    }

    fun saveDailyHealthData(date: String, steps: Long, sleepMinutes: Int, sleepSessions: List<SleepSessionInfo>, workouts: List<ExerciseSessionInfo>) {
        val hasHC = workouts.any { it.source == "health_connect" }
        val hasManual = workouts.any { it.source == "manual" }
        val docSource = when {
            hasHC && hasManual -> "mixed"
            hasManual -> "manual"
            else -> "health_connect"
        }
        val current = _healthDaily.value.toMutableMap()
        current[date] = DailyHealthData(
            date = date,
            steps = steps,
            sleepMinutes = sleepMinutes,
            sleepSessions = sleepSessions,
            workouts = workouts,
            syncedAt = Instant.now(),
            source = docSource
        )
        _healthDaily.value = current
    }

    fun addWorkout(date: String, type: String, durationMin: Int, startTime: Instant) {
        val currentData = getDailyHealthData(date)
        val updatedWorkouts = currentData.workouts.toMutableList()
        updatedWorkouts.add(ExerciseSessionInfo(type, durationMin, startTime, source = "manual"))

        saveDailyHealthData(
            date = date,
            steps = currentData.steps,
            sleepMinutes = currentData.sleepMinutes,
            sleepSessions = currentData.sleepSessions,
            workouts = updatedWorkouts
        )
    }

    // --- MealRepository Implementation ---

    override fun addMeal(
        date: String,
        inputType: String,
        description: String,
        items: List<MealItem>,
        totals: MealTotals,
        recommendation: String?,
        quality: MealQuality?
    ) {
        val entry = MealEntry(
            mealId = UUID.randomUUID().toString(),
            date = date,
            loggedAt = Instant.now(),
            inputType = inputType,
            description = description,
            items = items,
            totals = totals,
            recommendation = recommendation,
            quality = quality
        )
        _meals.value = _meals.value + entry
    }

    override fun deleteMeal(mealId: String) {
        _meals.value = _meals.value.filter { it.mealId != mealId }
    }

    override fun newMealId(): String = UUID.randomUUID().toString()

    override fun createPendingMeal(
        mealId: String, date: String, inputType: String,
        description: String, note: String?, localImagePath: String?
    ) {
        _meals.value = _meals.value + MealEntry(
            mealId = mealId, date = date, loggedAt = Instant.now(), inputType = inputType,
            description = description, items = emptyList(), totals = MealTotals(0, 0, 0, 0),
            status = MealStatus.ANALYZING, seen = false, note = note, localImagePath = localImagePath
        )
    }

    override fun completeMeal(
        mealId: String, items: List<MealItem>, totals: MealTotals,
        recommendation: String?, quality: MealQuality?
    ) {
        _meals.value = _meals.value.map {
            if (it.mealId == mealId) it.copy(
                items = items, totals = totals, recommendation = recommendation,
                quality = quality, status = MealStatus.COMPLETE, failureReason = null
            ) else it
        }
    }

    override fun failMeal(mealId: String, reason: String) {
        _meals.value = _meals.value.map {
            if (it.mealId == mealId) it.copy(status = MealStatus.FAILED, failureReason = reason) else it
        }
    }

    override fun retryMeal(mealId: String) {
        _meals.value = _meals.value.map {
            if (it.mealId == mealId) it.copy(status = MealStatus.ANALYZING, failureReason = null) else it
        }
    }

    override fun markMealSeen(mealId: String) {
        _meals.value = _meals.value.map { if (it.mealId == mealId) it.copy(seen = true) else it }
    }

    override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {
        _meals.value = _meals.value.map {
            if (it.mealId == mealId) it.copy(description = description, items = items, totals = totals) else it
        }
    }

    // --- BodyMeasurementRepository Implementation ---

    override fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String) {
        // Remove existing measurement for the same date if exists (idempotent)
        val filtered = _bodyMeasurements.value.filter { it.date != date }
        val entry = BodyMeasurement(date, weight, waist, hips, note)
        _bodyMeasurements.value = filtered + entry

        // If weight is updated, optionally update profile weight
        if (weight != null) {
            _profile.value = _profile.value?.copy(weightKg = weight, updatedAt = Instant.now())
        }
    }

    // --- WaterRepository Implementation ---

    override fun addWater(date: String, amountMl: Int) {
        val current = _waterLog.value.toMutableMap()
        val existing = current[date] ?: 0
        current[date] = existing + amountMl
        _waterLog.value = current
    }

    fun saveAiInsight(date: String, todayInsight: String, tomorrowInsight: String) {
        val current = _aiInsights.value.toMutableMap()
        current[date] = Pair(todayInsight, tomorrowInsight)
        _aiInsights.value = current
    }

    private fun setupMockData() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val twoDaysAgoStr = LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Setup mock daily health data
        val mockHealth = mutableMapOf<String, DailyHealthData>()
        
        // Today
        mockHealth[todayStr] = DailyHealthData(
            date = todayStr,
            steps = 8432L,
            sleepMinutes = 440, // 7h 20m
            sleepSessions = listOf(
                SleepSessionInfo(
                    start = Instant.now().minusSeconds(8 * 3600),
                    end = Instant.now().minusSeconds(20 * 60)
                )
            ),
            workouts = listOf(
                ExerciseSessionInfo(type = "Running", durationMin = 30, startTime = Instant.now().minusSeconds(4 * 3600)),
                ExerciseSessionInfo(type = "Strength", durationMin = 15, startTime = Instant.now().minusSeconds(2 * 3600))
            ),
            syncedAt = Instant.now(),
            source = "health_connect"
        )

        // Yesterday
        mockHealth[yesterdayStr] = DailyHealthData(
            date = yesterdayStr,
            steps = 10543L,
            sleepMinutes = 480, // 8h
            sleepSessions = listOf(
                SleepSessionInfo(
                    start = Instant.now().minusSeconds(32 * 3600),
                    end = Instant.now().minusSeconds(24 * 3600)
                )
            ),
            workouts = listOf(
                ExerciseSessionInfo(type = "Walking", durationMin = 45, startTime = Instant.now().minusSeconds(28 * 3600))
            ),
            syncedAt = Instant.now(),
            source = "health_connect"
        )

        // Two days ago
        mockHealth[twoDaysAgoStr] = DailyHealthData(
            date = twoDaysAgoStr,
            steps = 6211L,
            sleepMinutes = 390, // 6h 30m
            sleepSessions = listOf(
                SleepSessionInfo(
                    start = Instant.now().minusSeconds(56 * 3600),
                    end = Instant.now().minusSeconds(49 * 3600 + 30 * 60)
                )
            ),
            workouts = emptyList(),
            syncedAt = Instant.now(),
            source = "health_connect"
        )

        _healthDaily.value = mockHealth

        // Setup mock meals
        val mockMeals = mutableListOf<MealEntry>()
        // Breakfast
        mockMeals.add(
            MealEntry(
                mealId = UUID.randomUUID().toString(),
                date = todayStr,
                loggedAt = Instant.now(),
                inputType = "text",
                description = "חביתה משני ביצים, סלט ירקות קטן עם כפית שמן זית, שתי פרוסות לחם מלא",
                items = listOf(
                    MealItem("חביתה משתי ביצים", "1 מנה", 180, 14, 2, 12),
                    MealItem("סלט ירקות עם שמן זית", "1 מנה", 90, 1, 6, 7),
                    MealItem("לחם קמח מלא", "2 פרוסות", 160, 6, 30, 2)
                ),
                totals = MealTotals(430, 21, 38, 21)
            )
        )
        // Lunch
        mockMeals.add(
            MealEntry(
                mealId = UUID.randomUUID().toString(),
                date = todayStr,
                loggedAt = Instant.now(),
                inputType = "image",
                description = "חזה עוף בגריל, כוס אורז בסמטי מבושל, ברוקולי מאודה",
                items = listOf(
                    MealItem("חזה עוף בגריל", "150 גרם", 250, 46, 0, 5),
                    MealItem("אורז בסמטי מבושל", "1 כוס", 200, 4, 45, 0),
                    MealItem("ברוקולי מאודה", "1 כוס", 50, 3, 10, 0)
                ),
                totals = MealTotals(500, 53, 55, 5)
            )
        )

        // Yesterday lunch
        mockMeals.add(
            MealEntry(
                mealId = UUID.randomUUID().toString(),
                date = yesterdayStr,
                loggedAt = Instant.now(),
                inputType = "text",
                description = "פילה סלמון בתנור, פירה תפוחי אדמה, שעועית ירוקה",
                items = listOf(
                    MealItem("פילה סלמון בתנור", "200 גרם", 410, 40, 0, 26),
                    MealItem("פירה תפוחי אדמה", "1 כוס", 240, 4, 42, 6),
                    MealItem("שעועית ירוקה מוקפצת", "1 כוס", 70, 2, 10, 3)
                ),
                totals = MealTotals(720, 46, 52, 35)
            )
        )

        _meals.value = mockMeals

        // Setup mock measurements
        _bodyMeasurements.value = listOf(
            BodyMeasurement(todayStr, 75.0, 84.0, 96.0, "מרגיש טוב היום"),
            BodyMeasurement(yesterdayStr, 75.2, 84.5, 96.5, ""),
            BodyMeasurement(twoDaysAgoStr, 75.5, 85.0, 97.0, "התחלת שבוע")
        )

        // Setup mock water
        _waterLog.value = mapOf(
            todayStr to 1250, // 5 cups
            yesterdayStr to 2000,
            twoDaysAgoStr to 1500
        )

        // Setup mock AI insights
        _aiInsights.value = mapOf(
            todayStr to Pair(
                "הפעילות שלך היום מצוינת! צעדת כבר 8,432 צעדים מתוך יעד של 10,000. כדאי ללכת עוד קצת בערב כדי להגיע ליעד. צריכת החלבון שלך יפה מאוד ועומדת על 74 גרם.",
                "הדגשים שלך להיום: נסה להקפיד על הליכה קלה בבוקר כדי להתחיל את היום בתנועה, ודאג לשתות לפחות כוס מים אחת כל שעתיים."
            ),
            yesterdayStr to Pair(
                "עבודה מצוינת אתמול! עברת את יעד הצעדים עם 10,543 צעדים וישנת 8 שעות מלאות. המשך כך!",
                "הדגשים שלך להיום: התמקד בשינה איכותית הלילה על ידי הימנעות ממסכים שעה לפני השינה."
            )
        )
    }
}
