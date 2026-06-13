package com.myhealthtracker.app.data

import com.google.firebase.Timestamp
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class MealItem(
    val name: String,
    val quantity: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

data class MealTotals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

data class MealEntry(
    val mealId: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val loggedAt: Timestamp = Timestamp.now(),
    val inputType: String, // "text" | "image"
    val description: String,
    val items: List<MealItem>,
    val totals: MealTotals
)

data class BodyMeasurement(
    val date: String, // yyyy-MM-dd
    val weightKg: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val note: String = ""
)

object FakeRepository {
    // Current user auth state (mock)
    private val _isUserLoggedIn = MutableStateFlow(true)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    // Profile state
    private val _profile = MutableStateFlow<UserProfile?>(
        UserProfile(
            birthYear = 1995,
            weightKg = 75.0,
            heightCm = 178.0,
            gender = "זכר",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    )
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    // Daily health data (Steps, Sleep, Workouts) keyed by date "yyyy-MM-dd"
    private val _healthDaily = MutableStateFlow<Map<String, DailyHealthData>>(emptyMap())
    val healthDaily: StateFlow<Map<String, DailyHealthData>> = _healthDaily.asStateFlow()

    // Meals list
    private val _meals = MutableStateFlow<List<MealEntry>>(emptyList())
    val meals: StateFlow<List<MealEntry>> = _meals.asStateFlow()

    // Body measurements list
    private val _bodyMeasurements = MutableStateFlow<List<BodyMeasurement>>(emptyList())
    val bodyMeasurements: StateFlow<List<BodyMeasurement>> = _bodyMeasurements.asStateFlow()

    // Water log keyed by date -> ml
    private val _waterLog = MutableStateFlow<Map<String, Int>>(emptyMap())
    val waterLog: StateFlow<Map<String, Int>> = _waterLog.asStateFlow()

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
            gender = "זכר",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        setupMockData()
    }

    fun login() {
        _isUserLoggedIn.value = true
    }

    fun logout() {
        _isUserLoggedIn.value = false
    }

    fun saveProfile(birthYear: Int, weightKg: Double, heightCm: Double, gender: String) {
        _profile.value = UserProfile(
            birthYear = birthYear,
            weightKg = weightKg,
            heightCm = heightCm,
            gender = gender,
            createdAt = _profile.value?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

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
            syncedAt = Timestamp.now(),
            source = docSource
        )
        _healthDaily.value = current
    }

    fun addWorkout(date: String, type: String, durationMin: Int, startTime: Timestamp) {
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

    fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals) {
        val entry = MealEntry(
            date = date,
            inputType = inputType,
            description = description,
            items = items,
            totals = totals
        )
        _meals.value = _meals.value + entry
    }

    fun deleteMeal(mealId: String) {
        _meals.value = _meals.value.filter { it.mealId != mealId }
    }

    fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String) {
        // Remove existing measurement for the same date if exists (idempotent)
        val filtered = _bodyMeasurements.value.filter { it.date != date }
        val entry = BodyMeasurement(date, weight, waist, hips, note)
        _bodyMeasurements.value = filtered + entry

        // If weight is updated, optionally update profile weight
        if (weight != null) {
            _profile.value = _profile.value?.copy(weightKg = weight, updatedAt = Timestamp.now())
        }
    }

    fun addWater(date: String, amountMl: Int) {
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
                    start = Timestamp(Instant.now().minusSeconds(8 * 3600).epochSecond, 0),
                    end = Timestamp(Instant.now().minusSeconds(20 * 60).epochSecond, 0)
                )
            ),
            workouts = listOf(
                ExerciseSessionInfo(type = "Running", durationMin = 30, startTime = Timestamp(Instant.now().minusSeconds(4 * 3600).epochSecond, 0)),
                ExerciseSessionInfo(type = "Strength", durationMin = 15, startTime = Timestamp(Instant.now().minusSeconds(2 * 3600).epochSecond, 0))
            ),
            syncedAt = Timestamp.now(),
            source = "health_connect"
        )

        // Yesterday
        mockHealth[yesterdayStr] = DailyHealthData(
            date = yesterdayStr,
            steps = 10543L,
            sleepMinutes = 480, // 8h
            sleepSessions = listOf(
                SleepSessionInfo(
                    start = Timestamp(Instant.now().minusSeconds(32 * 3600).epochSecond, 0),
                    end = Timestamp(Instant.now().minusSeconds(24 * 3600).epochSecond, 0)
                )
            ),
            workouts = listOf(
                ExerciseSessionInfo(type = "Walking", durationMin = 45, startTime = Timestamp(Instant.now().minusSeconds(28 * 3600).epochSecond, 0))
            ),
            syncedAt = Timestamp.now(),
            source = "health_connect"
        )

        // Two days ago
        mockHealth[twoDaysAgoStr] = DailyHealthData(
            date = twoDaysAgoStr,
            steps = 6211L,
            sleepMinutes = 390, // 6h 30m
            sleepSessions = listOf(
                SleepSessionInfo(
                    start = Timestamp(Instant.now().minusSeconds(56 * 3600).epochSecond, 0),
                    end = Timestamp(Instant.now().minusSeconds(49 * 3600 + 30 * 60).epochSecond, 0)
                )
            ),
            workouts = emptyList(),
            syncedAt = Timestamp.now(),
            source = "health_connect"
        )

        _healthDaily.value = mockHealth

        // Setup mock meals
        val mockMeals = mutableListOf<MealEntry>()
        // Breakfast
        mockMeals.add(
            MealEntry(
                date = todayStr,
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
                date = todayStr,
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
                date = yesterdayStr,
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
