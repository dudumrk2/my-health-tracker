# Celebration Gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show joyful full-screen Lottie celebration overlays (with a gentle sound + haptic) when the user hits health milestones — step goal, 2nd/4th weekly workout, a high-quality AI meal, and meeting yesterday's calorie goal.

**Architecture:** A single celebration layer decoupled from the screens. Pure `CelebrationRules` decide *whether* to celebrate from raw primitives; a `CelebrationStore` (local DataStore) records "already celebrated" keys; a singleton `CelebrationController` dedups and emits events on a `SharedFlow`; a root-hosted `CelebrationOverlay` renders the animation/text/sound. Detection is wired into the ViewModels that already hold the data (`AddMealViewModel` for meals, `DashboardViewModel` for steps/workouts/calorie).

**Tech Stack:** Kotlin, Jetpack Compose, MVVM, Coroutines + Flow, Lottie (`lottie-compose`), AndroidX DataStore Preferences, JUnit4 + MockK + kotlinx-coroutines-test.

## Global Constraints

- minSdk 28, JDK 17, Kotlin 2.3.20, Compose BOM 2026.03.01.
- Code, comments, identifiers in **English**; user-facing strings in **Hebrew**.
- No blocking calls on the main thread — Coroutines for all I/O.
- Celebration "already shown" state is **local (DataStore) only — never written to Firestore** (it is a per-device display concern, not health data).
- Calorie-goal band is **±10%** of `goal.caloriesKcal`. Week is a **calendar week starting Sunday**; `weekId` = that Sunday's `yyyy-MM-dd`.
- Meal precedence: a meal that qualifies as "great" (`processedScore == 1`) fires **only** the great-meal celebration; "good" (`processedScore <= 2 && insulinImpact == "low"`) is the fallback. A single meal never fires both.
- New dependencies: `com.airbnb.android:lottie-compose` and `androidx.datastore:datastore-preferences`.
- Animations/sound are loaded **by resource name at runtime** (`resources.getIdentifier`). Missing assets degrade gracefully (text-only, silent) — the app compiles and runs before any asset file is added.
- Tests must pass before each task is considered complete.

## File Structure

**New files:**
- `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationEvent.kt` — `CelebrationType` enum + `CelebrationEvent` data class (pure).
- `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationRules.kt` — pure decision functions.
- `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationStore.kt` — `CelebrationStore` interface + `InMemoryCelebrationStore` + `DataStoreCelebrationStore`.
- `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationController.kt` — dedup + `SharedFlow` emitter.
- `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationVisuals.kt` — type → animation names + Hebrew message.
- `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationOverlay.kt` — Compose overlay (Lottie + sound + haptic).
- `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationRulesTest.kt`
- `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationControllerTest.kt`

**Modified files:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — add dependencies.
- `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt` — expose `celebrationController` + `initCelebrations(context)`.
- `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt` — call `initCelebrations`.
- `app/src/main/java/com/myhealthtracker/app/MainActivity.kt` — host the overlay at the root.
- `app/src/main/AndroidManifest.xml` — add `VIBRATE` permission.
- `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt` + its test — meal detection.
- `app/src/main/java/com/myhealthtracker/app/ui/dashboard/DashboardViewModel.kt` — steps/workout/calorie detection.

**User-supplied assets (NOT created by this plan — referenced by name):**
`app/src/main/res/raw/celeb_confetti.json`, `celeb_fireworks.json`, `celeb_clap.json`, `celeb_muscle.json`, `celeb_medal.json`, `celeb_trophy.json`, `celeb_chime.mp3`.

---

### Task 1: Celebration model + pure rules

Pure, JVM-testable decision logic. No Android dependencies.

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationEvent.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationRules.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationRulesTest.kt`

**Interfaces:**
- Consumes: `com.myhealthtracker.app.data.model.MealQuality` (existing: `processedScore: Int`, `insulinImpact: String`).
- Produces:
  - `enum class CelebrationType { STEP_GOAL, SECOND_WORKOUT, FOURTH_WORKOUT, GREAT_MEAL, GOOD_MEAL, CALORIE_GOAL }`
  - `data class CelebrationEvent(val type: CelebrationType, val dedupKey: String)`
  - `object CelebrationRules` with:
    - `fun startOfWeekSunday(date: LocalDate): LocalDate`
    - `fun weekId(date: LocalDate): String`
    - `fun stepGoal(steps: Long, goalSteps: Int, date: String): CelebrationEvent?`
    - `fun workoutMilestones(weeklyWorkoutCount: Int, weekId: String): List<CelebrationEvent>`
    - `fun mealQuality(quality: MealQuality?, mealKey: String): CelebrationEvent?`
    - `fun calorieGoalYesterday(yesterdayMealCount: Int, yesterdayCalories: Int, goalCalories: Int, yesterday: String): CelebrationEvent?`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationRulesTest.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

import com.myhealthtracker.app.data.model.MealQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CelebrationRulesTest {

    // ── Week anchoring (Sunday-start) ─────────────────────────────────────────
    @Test
    fun startOfWeekSunday_returnsSameDay_forSunday() {
        // 2026-06-21 is a Sunday
        val sunday = LocalDate.of(2026, 6, 21)
        assertEquals(sunday, CelebrationRules.startOfWeekSunday(sunday))
    }

    @Test
    fun startOfWeekSunday_returnsPrecedingSunday_forSaturday() {
        // 2026-06-27 is a Saturday → week started 2026-06-21
        assertEquals(LocalDate.of(2026, 6, 21), CelebrationRules.startOfWeekSunday(LocalDate.of(2026, 6, 27)))
    }

    @Test
    fun weekId_isTheSundayIsoDate() {
        assertEquals("2026-06-21", CelebrationRules.weekId(LocalDate.of(2026, 6, 24)))
    }

    // ── Step goal ─────────────────────────────────────────────────────────────
    @Test
    fun stepGoal_firesWhenStepsMeetGoal() {
        val e = CelebrationRules.stepGoal(8000, 8000, "2026-06-21")
        assertEquals(CelebrationType.STEP_GOAL, e?.type)
        assertEquals("steps-2026-06-21", e?.dedupKey)
    }

    @Test
    fun stepGoal_nullWhenBelowGoal() {
        assertNull(CelebrationRules.stepGoal(7999, 8000, "2026-06-21"))
    }

    @Test
    fun stepGoal_nullWhenGoalIsZero() {
        assertNull(CelebrationRules.stepGoal(5000, 0, "2026-06-21"))
    }

    // ── Workout milestones ─────────────────────────────────────────────────────
    @Test
    fun workoutMilestones_noneBelowTwo() {
        assertEquals(emptyList<CelebrationEvent>(), CelebrationRules.workoutMilestones(1, "2026-06-21"))
    }

    @Test
    fun workoutMilestones_secondOnlyAtTwoAndThree() {
        val two = CelebrationRules.workoutMilestones(2, "2026-06-21")
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT), two.map { it.type })
        assertEquals("workout2-2026-06-21", two.first().dedupKey)
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT), CelebrationRules.workoutMilestones(3, "2026-06-21").map { it.type })
    }

    @Test
    fun workoutMilestones_secondAndFourthAtFourPlus() {
        val four = CelebrationRules.workoutMilestones(4, "2026-06-21")
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT, CelebrationType.FOURTH_WORKOUT), four.map { it.type })
        assertEquals("workout4-2026-06-21", four[1].dedupKey)
    }

    // ── Meal quality (great vs good precedence) ────────────────────────────────
    @Test
    fun mealQuality_greatWhenScoreOne() {
        val e = CelebrationRules.mealQuality(MealQuality(processedScore = 1, insulinImpact = "high"), "m1")
        assertEquals(CelebrationType.GREAT_MEAL, e?.type)
        assertEquals("meal-great-m1", e?.dedupKey)
    }

    @Test
    fun mealQuality_goodWhenScoreTwoAndLowInsulin() {
        val e = CelebrationRules.mealQuality(MealQuality(processedScore = 2, insulinImpact = "low"), "m2")
        assertEquals(CelebrationType.GOOD_MEAL, e?.type)
        assertEquals("meal-good-m2", e?.dedupKey)
    }

    @Test
    fun mealQuality_nullWhenScoreTwoButInsulinNotLow() {
        assertNull(CelebrationRules.mealQuality(MealQuality(processedScore = 2, insulinImpact = "medium"), "m3"))
    }

    @Test
    fun mealQuality_nullWhenProcessedHigh() {
        assertNull(CelebrationRules.mealQuality(MealQuality(processedScore = 3, insulinImpact = "low"), "m4"))
    }

    @Test
    fun mealQuality_nullWhenQualityMissing() {
        assertNull(CelebrationRules.mealQuality(null, "m5"))
    }

    // ── Calorie goal yesterday (±10%, >= 3 meals) ──────────────────────────────
    @Test
    fun calorieGoal_firesWithinBandAndEnoughMeals() {
        val e = CelebrationRules.calorieGoalYesterday(3, 2100, 2000, "2026-06-20")
        assertEquals(CelebrationType.CALORIE_GOAL, e?.type)
        assertEquals("calorie-2026-06-20", e?.dedupKey)
    }

    @Test
    fun calorieGoal_firesAtExactLowerBoundary() {
        // 1800 is exactly -10% of 2000
        assertEquals(CelebrationType.CALORIE_GOAL, CelebrationRules.calorieGoalYesterday(3, 1800, 2000, "2026-06-20")?.type)
    }

    @Test
    fun calorieGoal_nullJustOutsideUpperBoundary() {
        // 2201 = +10.05%
        assertNull(CelebrationRules.calorieGoalYesterday(3, 2201, 2000, "2026-06-20"))
    }

    @Test
    fun calorieGoal_nullWhenFewerThanThreeMeals() {
        assertNull(CelebrationRules.calorieGoalYesterday(2, 2000, 2000, "2026-06-20"))
    }

    @Test
    fun calorieGoal_nullWhenGoalIsZero() {
        assertNull(CelebrationRules.calorieGoalYesterday(3, 0, 0, "2026-06-20"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.celebration.CelebrationRulesTest"`
Expected: FAIL — compilation error, `CelebrationRules` / `CelebrationEvent` unresolved.

- [ ] **Step 3: Write the model**

Create `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationEvent.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

/** The kind of milestone being celebrated. Drives both the animation and the copy. */
enum class CelebrationType {
    STEP_GOAL,
    SECOND_WORKOUT,
    FOURTH_WORKOUT,
    GREAT_MEAL,
    GOOD_MEAL,
    CALORIE_GOAL
}

/**
 * A single celebration to show. [dedupKey] makes state-derived celebrations fire
 * once (see CelebrationController.tryCelebrate); it is ignored for one-shot,
 * action-triggered celebrations (CelebrationController.celebrateNow).
 */
data class CelebrationEvent(
    val type: CelebrationType,
    val dedupKey: String
)
```

- [ ] **Step 4: Write the rules**

Create `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationRules.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

import com.myhealthtracker.app.data.model.MealQuality
import java.time.LocalDate
import kotlin.math.abs

/**
 * Pure decision logic for celebrations. Each function takes already-extracted
 * primitives and returns the event(s) to celebrate, or null/empty when no
 * milestone is met. No Android dependencies — fully unit-testable.
 */
object CelebrationRules {

    private const val CALORIE_TOLERANCE = 0.10

    /** Previous-or-same Sunday for [date] (calendar week starts Sunday). */
    fun startOfWeekSunday(date: LocalDate): LocalDate {
        // DayOfWeek.value: MON=1..SUN=7 → days since Sunday: SUN=0, MON=1, ... SAT=6.
        val daysSinceSunday = date.dayOfWeek.value % 7
        return date.minusDays(daysSinceSunday.toLong())
    }

    /** Stable id for the calendar week containing [date] — the Sunday's ISO date. */
    fun weekId(date: LocalDate): String = startOfWeekSunday(date).toString()

    fun stepGoal(steps: Long, goalSteps: Int, date: String): CelebrationEvent? =
        if (goalSteps > 0 && steps >= goalSteps) {
            CelebrationEvent(CelebrationType.STEP_GOAL, "steps-$date")
        } else null

    /** Emits the 2nd- and/or 4th-workout milestones reached this week. */
    fun workoutMilestones(weeklyWorkoutCount: Int, weekId: String): List<CelebrationEvent> {
        val events = mutableListOf<CelebrationEvent>()
        if (weeklyWorkoutCount >= 2) events += CelebrationEvent(CelebrationType.SECOND_WORKOUT, "workout2-$weekId")
        if (weeklyWorkoutCount >= 4) events += CelebrationEvent(CelebrationType.FOURTH_WORKOUT, "workout4-$weekId")
        return events
    }

    /** Great (score == 1) takes precedence over good (score <= 2 && low insulin). */
    fun mealQuality(quality: MealQuality?, mealKey: String): CelebrationEvent? {
        if (quality == null) return null
        return when {
            quality.processedScore == 1 ->
                CelebrationEvent(CelebrationType.GREAT_MEAL, "meal-great-$mealKey")
            quality.processedScore <= 2 && quality.insulinImpact == "low" ->
                CelebrationEvent(CelebrationType.GOOD_MEAL, "meal-good-$mealKey")
            else -> null
        }
    }

    /** Celebrates only when yesterday had >= 3 meals and stayed within ±10% of the goal. */
    fun calorieGoalYesterday(
        yesterdayMealCount: Int,
        yesterdayCalories: Int,
        goalCalories: Int,
        yesterday: String
    ): CelebrationEvent? {
        if (yesterdayMealCount < 3 || goalCalories <= 0) return null
        val withinBand = abs(yesterdayCalories - goalCalories).toDouble() / goalCalories <= CALORIE_TOLERANCE
        return if (withinBand) CelebrationEvent(CelebrationType.CALORIE_GOAL, "calorie-$yesterday") else null
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.celebration.CelebrationRulesTest"`
Expected: PASS (all tests green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationEvent.kt \
        app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationRules.kt \
        app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationRulesTest.kt
git commit -m "feat(celebrations): add pure celebration model and decision rules"
```

---

### Task 2: Celebration store + controller (+ DataStore dep, DI wiring)

Persistent dedup store and the singleton controller that emits events. Adds the DataStore dependency and wires the controller into the DI graph + app startup.

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationStore.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationController.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationControllerTest.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt`

**Interfaces:**
- Consumes: `CelebrationEvent` (Task 1).
- Produces:
  - `interface CelebrationStore { suspend fun hasCelebrated(key: String): Boolean; suspend fun markCelebrated(key: String) }`
  - `class InMemoryCelebrationStore : CelebrationStore`
  - `class DataStoreCelebrationStore(context: Context) : CelebrationStore`
  - `class CelebrationController(store: CelebrationStore, scope: CoroutineScope = …)` with
    `val events: SharedFlow<CelebrationEvent>`,
    `fun tryCelebrate(event: CelebrationEvent?)`, `fun tryCelebrate(events: List<CelebrationEvent>)`,
    `fun celebrateNow(event: CelebrationEvent?)`
  - `AppContainer.celebrationController: CelebrationController`, `AppContainer.initCelebrations(context: Context)`

- [ ] **Step 1: Add the DataStore dependency**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
datastore = "1.1.7"
```
Under `[libraries]` add:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

In `app/build.gradle.kts`, inside `dependencies { ... }` (next to the other Arch Components), add:
```kotlin
  // DataStore (local celebration dedup state)
  implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Write the failing controller test**

Create `app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationControllerTest.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CelebrationControllerTest {

    private val event = CelebrationEvent(CelebrationType.STEP_GOAL, "steps-2026-06-21")

    @Test
    fun `tryCelebrate emits once and suppresses duplicate keys`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.tryCelebrate(event)
        advanceUntilIdle()
        controller.tryCelebrate(event) // same dedup key
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(CelebrationType.STEP_GOAL, received.first().type)
    }

    @Test
    fun `tryCelebrate ignores null`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.tryCelebrate(null as CelebrationEvent?)
        advanceUntilIdle()

        assertEquals(0, received.size)
    }

    @Test
    fun `celebrateNow emits every time regardless of dedup`() = runTest {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        controller.celebrateNow(event)
        advanceUntilIdle()
        controller.celebrateNow(event)
        advanceUntilIdle()

        assertEquals(2, received.size)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.celebration.CelebrationControllerTest"`
Expected: FAIL — `CelebrationController` / `CelebrationStore` unresolved.

- [ ] **Step 4: Write the store**

Create `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationStore.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Records which celebrations have already been shown so each fires only once. */
interface CelebrationStore {
    suspend fun hasCelebrated(key: String): Boolean
    suspend fun markCelebrated(key: String)
}

/** Test/fallback implementation with no persistence. */
class InMemoryCelebrationStore : CelebrationStore {
    private val keys = mutableSetOf<String>()
    override suspend fun hasCelebrated(key: String): Boolean = keys.contains(key)
    override suspend fun markCelebrated(key: String) { keys.add(key) }
}

private val Context.celebrationDataStore by preferencesDataStore(name = "celebrations")

/** Local, per-device persistence. Never synced to Firestore (display concern only). */
class DataStoreCelebrationStore(context: Context) : CelebrationStore {
    private val appContext = context.applicationContext
    private val keysPref = stringSetPreferencesKey("celebrated_keys")

    override suspend fun hasCelebrated(key: String): Boolean {
        val prefs = appContext.celebrationDataStore.data.first()
        return prefs[keysPref]?.contains(key) == true
    }

    override suspend fun markCelebrated(key: String) {
        appContext.celebrationDataStore.edit { prefs ->
            prefs[keysPref] = (prefs[keysPref] ?: emptySet()) + key
        }
    }
}
```

- [ ] **Step 5: Write the controller**

Create `app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationController.kt`:

```kotlin
package com.myhealthtracker.app.data.celebration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Single source of celebration events. The overlay collects [events]; producers call
 * [tryCelebrate] (state-derived, deduped via [store]) or [celebrateNow] (one-shot
 * actions that need no dedup). Fire-and-forget: callers never block.
 */
class CelebrationController(
    private val store: CelebrationStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _events = MutableSharedFlow<CelebrationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CelebrationEvent> = _events.asSharedFlow()

    /** Fires once per [CelebrationEvent.dedupKey]; subsequent calls are suppressed. */
    fun tryCelebrate(event: CelebrationEvent?) {
        if (event == null) return
        scope.launch {
            if (!store.hasCelebrated(event.dedupKey)) {
                store.markCelebrated(event.dedupKey)
                _events.emit(event)
            }
        }
    }

    fun tryCelebrate(events: List<CelebrationEvent>) = events.forEach { tryCelebrate(it) }

    /** Always emits — for inherently one-shot triggers (e.g. saving a meal). */
    fun celebrateNow(event: CelebrationEvent?) {
        if (event == null) return
        scope.launch { _events.emit(event) }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.celebration.CelebrationControllerTest"`
Expected: PASS.

- [ ] **Step 7: Wire the controller into the DI graph**

In `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`, add imports near the top:
```kotlin
import android.content.Context
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.DataStoreCelebrationStore
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
```

Inside `object AppContainer`, add (e.g. after `accountRepository`/`activityRepository`):
```kotlin
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
```

- [ ] **Step 8: Initialize on app startup**

In `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt`, add the import:
```kotlin
import com.myhealthtracker.app.di.AppContainer
```
Then inside `onCreate()`, after `super.onCreate()` and before the App Check `try` block, add:
```kotlin
        AppContainer.initCelebrations(this)
```

- [ ] **Step 9: Verify the whole module still compiles and unit tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.celebration.*"`
Expected: PASS (rules + controller).

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationStore.kt \
        app/src/main/java/com/myhealthtracker/app/data/celebration/CelebrationController.kt \
        app/src/test/java/com/myhealthtracker/app/data/celebration/CelebrationControllerTest.kt \
        app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt \
        app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt
git commit -m "feat(celebrations): add dedup store, controller, and DI wiring"
```

---

### Task 3: Celebration overlay UI (+ Lottie dep, root host)

The visible layer: a full-screen overlay that plays a Lottie animation, shows the Hebrew message, plays a gentle sound (respecting silent mode) and a light haptic. Added at the app root so it covers every screen (including Add-Meal). No unit tests (Compose/system-context); verified by build.

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationVisuals.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationOverlay.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`

**Interfaces:**
- Consumes: `CelebrationType`, `CelebrationEvent` (Task 1); `AppContainer.celebrationController` (Task 2).
- Produces:
  - `object CelebrationVisuals { data class Visuals(val animations: List<String>, val message: String); fun forType(type: CelebrationType): Visuals }`
  - `@Composable fun CelebrationOverlay(controller: CelebrationController = AppContainer.celebrationController, modifier: Modifier = Modifier)`

- [ ] **Step 1: Add the Lottie dependency**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
lottie = "6.6.6"
```
Under `[libraries]` add:
```toml
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
```

In `app/build.gradle.kts`, inside `dependencies { ... }` (next to the Compose block), add:
```kotlin
  // Lottie animations for celebrations
  implementation(libs.lottie.compose)
```

- [ ] **Step 2: Add the VIBRATE permission**

In `app/src/main/AndroidManifest.xml`, add inside `<manifest>` (alongside the existing `<uses-permission>` entries):
```xml
    <uses-permission android:name="android.permission.VIBRATE" />
```

- [ ] **Step 3: Create the type → visuals mapping**

Create `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationVisuals.kt`:

```kotlin
package com.myhealthtracker.app.ui.celebration

import com.myhealthtracker.app.data.celebration.CelebrationType

/**
 * Maps each celebration type to its candidate animation file names (resolved from
 * res/raw by name at runtime) and its Hebrew encouragement message. Multiple
 * animations per type give variety — one is picked at random per firing.
 */
object CelebrationVisuals {

    data class Visuals(val animations: List<String>, val message: String)

    fun forType(type: CelebrationType): Visuals = when (type) {
        CelebrationType.STEP_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_fireworks"),
            "כל הכבוד! עמדת ביעד הצעדים היום 🎉"
        )
        CelebrationType.SECOND_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            "אימון שני השבוע — ממשיכים בכיף! 💪"
        )
        CelebrationType.FOURTH_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            "ארבעה אימונים השבוע, אלוף! 🔥"
        )
        CelebrationType.GREAT_MEAL -> Visuals(
            listOf("celeb_fireworks", "celeb_medal"),
            "ארוחה מצוינת! בחירה נקייה ובריאה 🥬"
        )
        CelebrationType.GOOD_MEAL -> Visuals(
            listOf("celeb_clap", "celeb_confetti"),
            "ארוחה טובה, כל הכבוד! 👏"
        )
        CelebrationType.CALORIE_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_trophy"),
            "עמדת ביעד הקלורי אתמול — מעולה! 🏆"
        )
    }
}
```

- [ ] **Step 4: Create the overlay composable**

Create `app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationOverlay.kt`:

```kotlin
package com.myhealthtracker.app.ui.celebration

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationEvent
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.delay

private const val TEXT_ONLY_DURATION_MS = 2200L

/**
 * Full-screen celebration layer. Hosted once at the app root so it overlays every
 * screen. Collects [controller] events and shows one at a time: a Lottie animation
 * (resolved from res/raw by name), a Hebrew message, a gentle sound (only off
 * silent mode), and a light haptic. Degrades to text-only when an asset is missing.
 */
@Composable
fun CelebrationOverlay(
    controller: CelebrationController = AppContainer.celebrationController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<CelebrationEvent?>(null) }

    LaunchedEffect(controller) {
        controller.events.collect { current = it }
    }

    val event = current ?: return
    val visuals = remember(event) { CelebrationVisuals.forType(event.type) }
    val animationName = remember(event) { visuals.animations.random() }
    val animationResId = remember(animationName) {
        context.resources.getIdentifier(animationName, "raw", context.packageName)
    }

    LaunchedEffect(event) {
        playChime(context)
        vibrate(context)
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { current = null },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (animationResId != 0) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationResId))
                val progress by animateLottieCompositionAsState(composition, iterations = 1)
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(220.dp)
                )
                LaunchedEffect(progress) {
                    if (progress >= 1f) current = null
                }
            } else {
                // No asset bundled yet — show text only and auto-dismiss.
                LaunchedEffect(event) {
                    delay(TEXT_ONLY_DURATION_MS)
                    current = null
                }
            }
            Text(
                text = visuals.message,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
            )
        }
    }
}

/** Plays the chime only when the ringer is in normal mode (respects silent/vibrate). */
private fun playChime(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    val resId = context.resources.getIdentifier("celeb_chime", "raw", context.packageName)
    if (resId == 0) return
    val player = MediaPlayer.create(context, resId) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    // minSdk 28 → VibrationEffect always available.
    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
}
```

- [ ] **Step 5: Host the overlay at the app root**

In `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`, add the imports:
```kotlin
import androidx.compose.foundation.layout.Box
import com.myhealthtracker.app.ui.celebration.CelebrationOverlay
```
Replace the `Surface { ... MainNavigation(...) ... }` body so the overlay sits above the navigation. The existing block is:
```kotlin
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val currentIntent by intentState
                    MainNavigation(
                        intent = currentIntent,
                        onIntentHandled = {
                            intentState.value = null
                            setIntent(Intent())
                        }
                    )
                }
```
Change it to:
```kotlin
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val currentIntent by intentState
                        MainNavigation(
                            intent = currentIntent,
                            onIntentHandled = {
                                intentState.value = null
                                setIntent(Intent())
                            }
                        )
                        // Root-hosted so celebrations overlay every screen.
                        CelebrationOverlay()
                    }
                }
```

- [ ] **Step 6: Verify the app builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Lottie/DataStore resolve; overlay compiles even with no asset files — resources are resolved by name at runtime.)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml \
        app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationVisuals.kt \
        app/src/main/java/com/myhealthtracker/app/ui/celebration/CelebrationOverlay.kt \
        app/src/main/java/com/myhealthtracker/app/MainActivity.kt
git commit -m "feat(celebrations): add root-hosted Lottie overlay with sound and haptic"
```

---

### Task 4: Meal-quality detection in AddMealViewModel

Fire a great/good-meal celebration the moment an AI-analyzed meal is saved. One-shot, so it uses `celebrateNow`. Fully testable via the existing fake-repo test harness.

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

**Interfaces:**
- Consumes: `CelebrationController.celebrateNow` (Task 2), `CelebrationRules.mealQuality` (Task 1).
- Produces: `AddMealViewModel` gains a third constructor param `celebrationController: CelebrationController = AppContainer.celebrationController`.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`, add these imports at the top (next to the existing imports):
```kotlin
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationEvent
import com.myhealthtracker.app.data.celebration.CelebrationType
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
```
Add these test methods inside the class:
```kotlin
    @Test
    fun `saving a great AI meal emits a great-meal celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Salad", "1", 120, 5, 10, 3)),
                totals = MealTotals(120, 5, 10, 3),
                lowConfidence = false,
                quality = MealQuality(processedScore = 1, insulinImpact = "low")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("salad"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(CelebrationType.GREAT_MEAL, received.first().type)
    }

    @Test
    fun `saving a good AI meal emits a good-meal celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Bowl", "1", 300, 20, 30, 8)),
                totals = MealTotals(300, 20, 30, 8),
                lowConfidence = false,
                quality = MealQuality(processedScore = 2, insulinImpact = "low")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("bowl"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(CelebrationType.GOOD_MEAL, received.first().type)
    }

    @Test
    fun `saving a processed AI meal emits no celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Pizza", "1", 800, 25, 90, 35)),
                totals = MealTotals(800, 25, 90, 35),
                lowConfidence = false,
                quality = MealQuality(processedScore = 4, insulinImpact = "high")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("pizza"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(0, received.size)
    }

    @Test
    fun `manual meal save emits no celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = backgroundScope)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer(), controller)
        vm.switchToManualFallback()
        vm.onManualCalChange("500")
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(0, received.size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: FAIL — `AddMealViewModel` has no 3-arg constructor; `CelebrationController` unresolved at call site.

- [ ] **Step 3: Add the dependency and detection to the ViewModel**

In `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`, add imports:
```kotlin
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
```
Change the constructor to add the third parameter:
```kotlin
class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analyzer: MealAnalyzer = AppContainer.mealAnalyzer,
    private val celebrationController: CelebrationController = AppContainer.celebrationController
) : ViewModel() {
```
In `saveMeal()`, immediately after the `mealRepository.addMeal(...)` call and before `_isSaved.value = true`, add:
```kotlin
            // Celebrate a high-quality AI meal (great > good). Manual meals have no
            // AI quality, so this is null for them and nothing fires.
            if (!manual) {
                celebrationController.celebrateNow(
                    CelebrationRules.mealQuality(_quality.value, today())
                )
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: PASS (new celebration tests + all pre-existing AddMealViewModel tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt \
        app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
git commit -m "feat(celebrations): celebrate great/good AI meals on save"
```

---

### Task 5: Steps / workout / calorie detection in DashboardViewModel

Wire the three state-derived triggers into the Dashboard's existing data combine. Logic lives in the already-tested `CelebrationRules`; this task is the thin pass-through that extracts primitives and forwards deduped events. Verified by build (the project has no DashboardViewModel unit test; the decision logic is covered by `CelebrationRulesTest`).

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/dashboard/DashboardViewModel.kt`

**Interfaces:**
- Consumes: `CelebrationController.tryCelebrate` (Task 2); `CelebrationRules` (Task 1); existing `GoalCalculator.compute`, `HealthGoals`.
- Produces: no new public surface (internal detection only).

- [ ] **Step 1: Add imports and the controller dependency**

In `app/src/main/java/com/myhealthtracker/app/ui/dashboard/DashboardViewModel.kt`, add imports:
```kotlin
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
import com.myhealthtracker.app.data.goals.GoalCalculator
```
Add the controller to the constructor (after `insightsRefresher`, before `uidProvider`):
```kotlin
    private val celebrationController: CelebrationController = AppContainer.celebrationController,
```

- [ ] **Step 2: Detect milestones inside the combine transform**

In `DashboardViewModel`, the combine block already computes `localizedProfile`, `healthList`, `todayHealth`, `meals` (the weekly list), `weeklyAerobicMinutes`, etc. Find this point — right after `val unifiedInsight = pickInsight(...)` and before the `DashboardState(` constructor call — and insert the detection block:

```kotlin
        // ── Celebrations (state-derived; each fires once via the dedup store) ──
        // Goals use the raw (English-gender) profile; localizedProfile would break
        // GoalCalculator's "male"/"female" checks.
        val goals = GoalCalculator.compute(rawProfile ?: UserProfile())

        celebrationController.tryCelebrate(
            CelebrationRules.stepGoal(todayHealth.steps, goals.steps, todayStr)
        )

        val weekStart = CelebrationRules.startOfWeekSunday(today)
        val weeklyWorkoutCount = healthList
            .filter { day ->
                runCatching {
                    val d = LocalDate.parse(day.date)
                    !d.isBefore(weekStart) && !d.isAfter(today)
                }.getOrDefault(false)
            }
            .sumOf { it.workouts.size }
        celebrationController.tryCelebrate(
            CelebrationRules.workoutMilestones(weeklyWorkoutCount, CelebrationRules.weekId(today))
        )

        val yesterdayStr = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayMeals = meals.filter { it.date == yesterdayStr }
        celebrationController.tryCelebrate(
            CelebrationRules.calorieGoalYesterday(
                yesterdayMealCount = yesterdayMeals.size,
                yesterdayCalories = yesterdayMeals.sumOf { it.totals.calories },
                goalCalories = goals.caloriesKcal,
                yesterday = yesterdayStr
            )
        )
```

Notes for the implementer:
- `rawProfile`, `healthList`, `todayHealth`, `meals`, and `today` (`val today = LocalDate.now()`) already exist as locals in this block — reuse them, do not redeclare. `UserProfile` and `DateTimeFormatter` are already imported.
- `meals` here is the full meal list from `mealRepository.meals` (local `val meals = array[2] as List<MealEntry>`), so filtering it by `yesterdayStr` always finds yesterday's meals. Use `meals`, not the filtered `weeklyMeals`.
- `tryCelebrate` is fire-and-forget and deduped, so calling it from the transform on every emission is safe (no duplicate overlays).

- [ ] **Step 3: Verify the app builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full unit-test suite to confirm nothing regressed**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/dashboard/DashboardViewModel.kt
git commit -m "feat(celebrations): detect step, workout, and calorie milestones on dashboard"
```

---

## Manual verification (after assets are added)

These require the user's Lottie/sound files in `res/raw` and a device/emulator; they are not automated:
1. Drop `celeb_*.json` and `celeb_chime.mp3` into `app/src/main/res/raw/`, rebuild, install.
2. Step goal: a day whose synced steps ≥ goal → overlay on the Dashboard.
3. Workouts: log a 2nd (then 4th) workout in the current Sun–Sat week → overlay.
4. Meal: analyze a meal the AI scores `processedScore == 1` (great) or `<= 2 && low insulin` (good) → overlay on save.
5. Calorie: a prior day with ≥ 3 meals whose total is within ±10% of the calorie goal → overlay when the Dashboard loads the next day.
6. Reopen each screen → the celebration does **not** replay (dedup works).
7. Put the phone on silent → no sound, animation still plays.
```
