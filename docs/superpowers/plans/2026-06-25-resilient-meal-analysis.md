# Resilient Meal Analysis Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn meal photo/text analysis into a durable background pipeline: persist the meal immediately as "analyzing", run `analyzeMeal` in WorkManager with retries, store the photo on-device, auto-save the result, notify when done, intercept unseen results with a celebration, and recover failures from a Food-screen banner.

**Architecture:** On "send", the meal is written to Firestore as `status="analyzing"` (with the local image path), and a unique `MealAnalysisWorker` is enqueued. The worker reads the local image, calls the existing `analyzeMeal` Cloud Function, and updates the same document to `complete` (with items/totals/quality) or `failed`. The existing Firestore snapshot listener streams these updates to every surface. A completed-but-unseen meal pops its result/edit screen on the next normal Dashboard entry (with a celebration if quality warrants); failures surface as a Food-screen banner and a tappable card.

**Tech Stack:** Kotlin, Jetpack Compose, MVVM, Coroutines/Flow, WorkManager, Cloud Firestore (offline cache + snapshot listener), Firebase Functions, Coil. JUnit4 + kotlinx-coroutines-test + MockK for unit tests.

## Global Constraints

- Kotlin (latest stable), minSdk 28, targetSdk 35, JDK 17. Compose + MVVM + Coroutines/Flow only.
- AI keys never on the client: all Gemini calls go through the existing `analyzeMeal` Cloud Function (unchanged here).
- **Meal photos never go to the cloud.** They may be cached on-device only (this plan); only the local file *path* string is stored in Firestore.
- No blocking calls on the main thread — all I/O on Coroutines / WorkManager.
- All Firestore access stays under `users/{uid}` (existing repository already enforces this).
- User-facing strings are Hebrew; code/comments/identifiers English.
- Tests are mandatory per task; a task is not done until its tests pass and the build succeeds.
- Run unit tests with `./gradlew test`; build with `./gradlew assembleDebug`. `./gradlew test` compiles the **entire** `main` + unit-test source sets, so every task must leave both compiling. (`androidTest` is only compiled by `connectedAndroidTest`, not by `test`.)
- Work happens on branch `feat/resilient-meal-analysis` (already checked out).

---

## File Structure

**Created:**
- `data/meal/MealAnalysisInput.kt` — WorkManager input DTO + `MealEntry.toAnalysisInput()`.
- `data/meal/MealAnalysisRunner.kt` — pure orchestration of one analysis attempt (success/retry/fail).
- `data/meal/MealAnalysisLauncher.kt` — interface + WorkManager-backed impl used by the ViewModel.
- `sync/MealAnalysisWorker.kt` — thin CoroutineWorker shell around the runner.
- `sync/MealAnalysisScheduler.kt` — `enqueueUniqueWork` per mealId.
- `util/MealImageStore.kt` — on-device image save/read/delete/orphan-sweep.
- `notification/MealAnalysisNotifier.kt` — completion/failure notifications + channel.
- `app/AppForegroundTracker.kt` — process foreground flag.
- `ui/meal/MealResultContent.kt` — reusable result/editor composable.
- `ui/meal/MealEditViewModel.kt` / `ui/meal/MealEditScreen.kt` — edit a saved meal.
- `ui/meal/UnseenMealRouter.kt` — pure `pickUnseenMealToShow` + totals/failed-count helpers.
- Test files mirroring the above under `app/src/test/java/...`.

**Modified:**
- `data/model/Meal.kt`, `data/meal/MealRepository.kt`, `data/meal/FirestoreMealRepository.kt`.
- `ui/meal/AddMealViewModel.kt`, `ui/meal/AddMealScreen.kt`.
- `ui/food/FoodViewModel.kt`, `ui/food/FoodScreen.kt`.
- `NavigationKeys.kt`, `Navigation.kt`, `ui/main/MainScreen.kt`.
- `notification/QuickActionsNotificationManager.kt`.
- `di/AppContainer.kt`, `MyHealthApp.kt`, `MainActivity.kt`.
- Test fakes: `data/FakeRepository.kt`, `ui/meal/AddMealViewModelTest.kt`.
- Docs: `CLAUDE.md`, `docs/HLD-health-tracker.md`, `docs/CHANGELOG.md`.

(All `app/src/main/java/com/myhealthtracker/app/...` paths are abbreviated above; full paths appear in each task.)

---

## Task 1: Meal model — status, image/seen fields, totals helper

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/data/model/Meal.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/model/MealTotalsTest.kt`

**Interfaces:**
- Produces: `object MealStatus { const val ANALYZING; const val COMPLETE; const val FAILED }`; `MealTotals.fromItems(items): MealTotals`; `MealEntry` gains `status: String = MealStatus.COMPLETE`, `localImagePath: String? = null`, `note: String? = null`, `failureReason: String? = null`, `seen: Boolean = true`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/model/MealTotalsTest.kt`:

```kotlin
package com.myhealthtracker.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MealTotalsTest {
    @Test
    fun `fromItems sums each macro across items`() {
        val items = listOf(
            MealItem("A", "1", 100, 10, 5, 2),
            MealItem("B", "1", 250, 20, 30, 8)
        )
        val totals = MealTotals.fromItems(items)
        assertEquals(350, totals.calories)
        assertEquals(30, totals.proteinG)
        assertEquals(35, totals.carbsG)
        assertEquals(10, totals.fatG)
    }

    @Test
    fun `fromItems on empty list is all zeros`() {
        val totals = MealTotals.fromItems(emptyList())
        assertEquals(0, totals.calories)
        assertEquals(0, totals.proteinG)
    }

    @Test
    fun `meal entry defaults to complete and seen for backward compatibility`() {
        val entry = MealEntry(
            mealId = "1", date = "2026-06-25", loggedAt = java.time.Instant.now(),
            inputType = "text", description = "x",
            items = emptyList(), totals = MealTotals(0, 0, 0, 0)
        )
        assertEquals(MealStatus.COMPLETE, entry.status)
        assertEquals(true, entry.seen)
        assertEquals(null, entry.localImagePath)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.model.MealTotalsTest"`
Expected: FAIL — `MealStatus` / `fromItems` / new fields unresolved.

- [ ] **Step 3: Edit `Meal.kt`**

Add the status object, the companion helper, and the new fields:

```kotlin
object MealStatus {
    const val ANALYZING = "analyzing"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
}

data class MealTotals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
) {
    companion object {
        fun fromItems(items: List<MealItem>) = MealTotals(
            calories = items.sumOf { it.calories },
            proteinG = items.sumOf { it.proteinG },
            carbsG = items.sumOf { it.carbsG },
            fatG = items.sumOf { it.fatG }
        )
    }
}
```

Extend `MealEntry` (keep existing fields; add the new trailing ones):

```kotlin
data class MealEntry(
    val mealId: String,
    val date: String, // yyyy-MM-dd
    val loggedAt: Instant,
    val inputType: String, // "text" | "image"
    val description: String,
    val items: List<MealItem>,
    val totals: MealTotals,
    val recommendation: String? = null,
    val quality: MealQuality? = null,
    // Resilient-analysis pipeline fields. Defaults keep legacy/manual docs "complete" + "seen".
    val status: String = MealStatus.COMPLETE,
    val localImagePath: String? = null,
    val note: String? = null,
    val failureReason: String? = null,
    val seen: Boolean = true
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.model.MealTotalsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/model/Meal.kt app/src/test/java/com/myhealthtracker/app/data/model/MealTotalsTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): add status/image/seen fields and MealTotals.fromItems

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Repository — pending/complete/fail/seen/update/retry + status-aware mapping + fakes

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/data/meal/MealRepository.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt`
- Modify: `app/src/test/java/com/myhealthtracker/app/data/FakeRepository.kt` (keep test sources compiling)
- Modify: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt` (stub new methods on its `FakeMealRepo`; fully rewritten in Task 7)
- Test: `app/src/test/java/com/myhealthtracker/app/data/meal/MealDocMappingTest.kt`

**Interfaces:**
- Consumes: `MealStatus`, `MealTotals.fromItems` (Task 1).
- Produces (on `MealRepository`): `newMealId()`, `createPendingMeal(mealId,date,inputType,description,note,localImagePath)`, `completeMeal(mealId,items,totals,recommendation,quality)`, `failMeal(mealId,reason)`, `markMealSeen(mealId)`, `updateMeal(mealId,description,items,totals)`, `retryMeal(mealId)`, plus existing `meals`/`addMeal`/`deleteMeal`. Pure top-level `mealEntryFromMap(id: String, data: Map<String, Any?>): MealEntry?`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/meal/MealDocMappingTest.kt`:

```kotlin
package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDocMappingTest {
    @Test
    fun `legacy doc without status maps to complete and seen`() {
        val entry = mealEntryFromMap(
            "id1",
            mapOf(
                "date" to "2026-06-25", "inputType" to "text", "description" to "legacy",
                "items" to emptyList<Any>(),
                "totals" to mapOf("calories" to 200, "proteinG" to 10, "carbsG" to 20, "fatG" to 5)
            )
        )!!
        assertEquals(MealStatus.COMPLETE, entry.status)
        assertEquals(true, entry.seen)
        assertEquals(200, entry.totals.calories)
    }

    @Test
    fun `analyzing doc maps status path and seen flag`() {
        val entry = mealEntryFromMap(
            "id2",
            mapOf(
                "date" to "2026-06-25", "inputType" to "image", "description" to "pending",
                "status" to "analyzing", "seen" to false,
                "localImagePath" to "/data/x/meal_images/p.jpg", "note" to "no sauce"
            )
        )!!
        assertEquals(MealStatus.ANALYZING, entry.status)
        assertEquals(false, entry.seen)
        assertEquals("/data/x/meal_images/p.jpg", entry.localImagePath)
        assertEquals("no sauce", entry.note)
    }

    @Test
    fun `failed doc carries failureReason`() {
        val entry = mealEntryFromMap(
            "id3",
            mapOf(
                "date" to "2026-06-25", "inputType" to "text", "description" to "x",
                "status" to "failed", "seen" to false, "failureReason" to "שירות ה-AI עמוס"
            )
        )!!
        assertEquals(MealStatus.FAILED, entry.status)
        assertEquals("שירות ה-AI עמוס", entry.failureReason)
    }

    @Test
    fun `missing date returns null`() {
        assertEquals(null, mealEntryFromMap("id4", mapOf("inputType" to "text")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.meal.MealDocMappingTest"`
Expected: FAIL — `mealEntryFromMap` unresolved (and possibly other test sources fail to compile until Step 5 fakes are updated; that is expected and fixed below).

- [ ] **Step 3: Replace the `MealRepository` interface**

```kotlin
interface MealRepository {
    val meals: StateFlow<List<MealEntry>>

    /** Client-side document id, available offline, so the caller can name the image file and enqueue work. */
    fun newMealId(): String

    /** Writes a doc with status="analyzing", seen=false, empty items/totals. */
    fun createPendingMeal(
        mealId: String, date: String, inputType: String,
        description: String, note: String?, localImagePath: String?
    )

    fun completeMeal(
        mealId: String, items: List<MealItem>, totals: MealTotals,
        recommendation: String?, quality: MealQuality?
    )

    fun failMeal(mealId: String, reason: String)

    /** Flip a failed meal back to analyzing (clears the failure reason) before a manual retry. */
    fun retryMeal(mealId: String)

    fun markMealSeen(mealId: String)

    fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals)

    /** Direct save for manual meals: status="complete", seen=true. */
    fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals,
        recommendation: String? = null, quality: MealQuality? = null
    )

    fun deleteMeal(mealId: String)
}
```

- [ ] **Step 4: Implement in `FirestoreMealRepository.kt`**

Add `import com.myhealthtracker.app.data.model.MealStatus`. Add a `mealsCollection` helper, route the snapshot through the pure mapper, and implement the new methods. Replace `attachListener` and the method bodies:

```kotlin
    private fun mealsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("meals")

    private fun attachListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = mealsCollection(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _meals.value = snap.documents
                    .mapNotNull { doc -> mealEntryFromMap(doc.id, doc.data ?: emptyMap()) }
                    .sortedByDescending { it.loggedAt }
            }
    }

    override fun newMealId(): String {
        val uid = auth.currentUser?.uid ?: return java.util.UUID.randomUUID().toString()
        return mealsCollection(uid).document().id
    }

    override fun createPendingMeal(
        mealId: String, date: String, inputType: String,
        description: String, note: String?, localImagePath: String?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "date" to date, "loggedAt" to Timestamp.now(), "inputType" to inputType,
            "description" to description, "items" to emptyList<Map<String, Any>>(),
            "totals" to MealTotals(0, 0, 0, 0).toMap(),
            "status" to MealStatus.ANALYZING, "seen" to false, "aiModel" to "gemini-2.5-flash"
        )
        if (note != null) data["note"] = note
        if (localImagePath != null) data["localImagePath"] = localImagePath
        mealsCollection(uid).document(mealId).set(data)
    }

    override fun completeMeal(
        mealId: String, items: List<MealItem>, totals: MealTotals,
        recommendation: String?, quality: MealQuality?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "items" to items.map { it.toMap() },
            "totals" to totals.toMap(),
            "status" to MealStatus.COMPLETE,
            "failureReason" to com.google.firebase.firestore.FieldValue.delete()
        )
        if (recommendation != null) data["recommendation"] = recommendation
        if (quality != null) data["quality"] = quality.toMap()
        mealsCollection(uid).document(mealId).update(data)
    }

    override fun failMeal(mealId: String, reason: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId)
            .update(mapOf("status" to MealStatus.FAILED, "failureReason" to reason))
    }

    override fun retryMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update(
            mapOf("status" to MealStatus.ANALYZING,
                  "failureReason" to com.google.firebase.firestore.FieldValue.delete())
        )
    }

    override fun markMealSeen(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update("seen", true)
    }

    override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update(
            mapOf("description" to description, "items" to items.map { it.toMap() }, "totals" to totals.toMap())
        )
    }

    override fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "date" to date, "loggedAt" to Timestamp.now(), "inputType" to inputType,
            "description" to description, "items" to items.map { it.toMap() },
            "totals" to totals.toMap(), "status" to MealStatus.COMPLETE, "seen" to true,
            "aiModel" to "gemini-2.5-flash"
        )
        if (recommendation != null) data["recommendation"] = recommendation
        if (quality != null) data["quality"] = quality.toMap()
        mealsCollection(uid).add(data)
    }

    override fun deleteMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).delete()
    }
```

Delete the old private `DocumentSnapshot.toMealEntry()` extension and replace it with this pure top-level function:

```kotlin
fun mealEntryFromMap(id: String, data: Map<String, Any?>): MealEntry? {
    val date = data["date"] as? String ?: return null
    val itemsRaw = data["items"] as? List<*> ?: emptyList<Any>()
    val items = itemsRaw.mapNotNull { e ->
        val m = e as? Map<*, *> ?: return@mapNotNull null
        MealItem(
            name = m["name"] as? String ?: "",
            quantity = m["quantity"] as? String ?: "",
            calories = (m["calories"] as? Number)?.toInt() ?: 0,
            proteinG = (m["proteinG"] as? Number)?.toInt() ?: 0,
            carbsG = (m["carbsG"] as? Number)?.toInt() ?: 0,
            fatG = (m["fatG"] as? Number)?.toInt() ?: 0
        )
    }
    val t = data["totals"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val totals = MealTotals(
        calories = (t["calories"] as? Number)?.toInt() ?: 0,
        proteinG = (t["proteinG"] as? Number)?.toInt() ?: 0,
        carbsG = (t["carbsG"] as? Number)?.toInt() ?: 0,
        fatG = (t["fatG"] as? Number)?.toInt() ?: 0
    )
    val q = data["quality"] as? Map<*, *>
    val quality = q?.let {
        MealQuality(
            processedScore = (it["processedScore"] as? Number)?.toInt() ?: 1,
            hasComplexCarbs = it["hasComplexCarbs"] as? Boolean ?: false,
            hasSimpleCarbs = it["hasSimpleCarbs"] as? Boolean ?: false,
            hasHealthyFats = it["hasHealthyFats"] as? Boolean ?: false,
            insulinImpact = it["insulinImpact"] as? String ?: "low"
        )
    }
    return MealEntry(
        mealId = id, date = date,
        loggedAt = (data["loggedAt"] as? Timestamp)?.toDate()?.toInstant() ?: Instant.now(),
        inputType = data["inputType"] as? String ?: "text",
        description = data["description"] as? String ?: "",
        items = items, totals = totals,
        recommendation = data["recommendation"] as? String,
        quality = quality,
        status = data["status"] as? String ?: MealStatus.COMPLETE,
        localImagePath = data["localImagePath"] as? String,
        note = data["note"] as? String,
        failureReason = data["failureReason"] as? String,
        seen = data["seen"] as? Boolean ?: true
    )
}
```

- [ ] **Step 5: Keep the test fakes compiling**

In `app/src/test/java/com/myhealthtracker/app/data/FakeRepository.kt`, add these overrides inside the `// --- MealRepository Implementation ---` block (alongside `addMeal`/`deleteMeal`):

```kotlin
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
```

Add `import com.myhealthtracker.app.data.model.MealStatus` to that file.

In `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`, the existing `FakeMealRepo` only overrides `addMeal`/`deleteMeal`. Add no-op stubs so it compiles (this whole file is replaced in Task 7):

```kotlin
        override fun newMealId() = "id"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
```

> Note: `app/src/androidTest/.../FirestoreMealWaterEmulatorTest.kt` uses the concrete `FirestoreMealRepository`; the new methods don't break it, and `androidTest` isn't compiled by `./gradlew test`. Leave it for the instrumented suite.

- [ ] **Step 6: Run tests + full unit compile**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.meal.MealDocMappingTest"`
Expected: PASS, and the whole unit-test source set compiles (no unimplemented-member errors).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/meal/MealRepository.kt app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt app/src/test/java/com/myhealthtracker/app/data/FakeRepository.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt app/src/test/java/com/myhealthtracker/app/data/meal/MealDocMappingTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): repository pending/complete/fail/retry/seen/update + pure doc mapper

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: On-device image store (save / read / delete / orphan sweep)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/util/MealImageStore.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/util/MealImageStoreTest.kt`

**Interfaces:**
- Produces:
  - `ImageEncoder.bitmapToJpegBytes(bitmap): ByteArray`, `ImageEncoder.uriToJpegBytes(context, uri): ByteArray?` (existing `uriToBase64Jpeg`/`bitmapToBase64Jpeg` keep working).
  - `object MealImageStore`: `dir(context): File`, `suspend saveFromUri(context, uri): String?`, `readAsBase64(path): String?`, `delete(path: String?)`, `sweepOrphans(dir: File, referencedPaths: Set<String>): Int`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/util/MealImageStoreTest.kt`:

```kotlin
package com.myhealthtracker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MealImageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(dir: File, name: String): File =
        File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `sweepOrphans deletes files not referenced by any meal`() {
        val dir = tmp.newFolder("meal_images")
        val keep = touch(dir, "a.jpg")
        val orphan = touch(dir, "b.jpg")
        val deleted = MealImageStore.sweepOrphans(dir, setOf(keep.absolutePath))
        assertEquals(1, deleted)
        assertTrue(keep.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun `sweepOrphans on empty references deletes all`() {
        val dir = tmp.newFolder("meal_images")
        touch(dir, "a.jpg"); touch(dir, "b.jpg")
        assertEquals(2, MealImageStore.sweepOrphans(dir, emptySet()))
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `sweepOrphans is a no-op when dir missing`() {
        assertEquals(0, MealImageStore.sweepOrphans(File(tmp.root, "nope"), emptySet()))
    }

    @Test
    fun `delete removes the file and tolerates null`() {
        val dir = tmp.newFolder("meal_images")
        val f = touch(dir, "x.jpg")
        MealImageStore.delete(null)
        MealImageStore.delete(f.absolutePath)
        assertFalse(f.exists())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.util.MealImageStoreTest"`
Expected: FAIL — `MealImageStore` unresolved.

- [ ] **Step 3: Extend `ImageEncoder.kt`**

Add JPEG-bytes helpers and route base64 through them (replace the existing `bitmapToBase64Jpeg` body):

```kotlin
    fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val scaled = downscale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled != bitmap) scaled.recycle()
        return out.toByteArray()
    }

    fun bitmapToBase64Jpeg(bitmap: Bitmap): String =
        Base64.encodeToString(bitmapToJpegBytes(bitmap), Base64.NO_WRAP)

    fun uriToJpegBytes(context: Context, uri: Uri): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        return bitmapToJpegBytes(original)
    }
```

- [ ] **Step 4: Create `MealImageStore.kt`**

```kotlin
package com.myhealthtracker.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device, app-private storage for meal photos. Images NEVER leave the device — only
 * the absolute file path is referenced from the Firestore meal document. Files live in
 * filesDir/meal_images (not cacheDir, so the OS does not evict them).
 */
object MealImageStore {
    private const val DIR = "meal_images"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Downscales [uri] to a JPEG, writes a new file, and returns its absolute path (or null). */
    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = ImageEncoder.uriToJpegBytes(context, uri) ?: return@withContext null
        val file = File.createTempFile("meal_", ".jpg", dir(context))
        file.writeBytes(bytes)
        file.absolutePath
    }

    /** Reads the file at [path] and returns base64 (NO_WRAP), or null if missing/unreadable. */
    fun readAsBase64(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    fun delete(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    /** Deletes every file in [dir] whose absolute path is not in [referencedPaths]. Returns count deleted. */
    fun sweepOrphans(dir: File, referencedPaths: Set<String>): Int {
        val files = dir.listFiles() ?: return 0
        var deleted = 0
        for (f in files) if (f.isFile && f.absolutePath !in referencedPaths && f.delete()) deleted++
        return deleted
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.myhealthtracker.app.util.MealImageStoreTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt app/src/main/java/com/myhealthtracker/app/util/MealImageStore.kt app/src/test/java/com/myhealthtracker/app/util/MealImageStoreTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): on-device MealImageStore with orphan sweep

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Analysis input DTO + pure runner (success / retry / fail)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisInput.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisRunner.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/meal/MealAnalysisRunnerTest.kt`

**Interfaces:**
- Consumes: `MealAnalyzer`/`MealAnalysisException`/`MealAnalysisResult` (existing), `MealRepository` (Task 2), `MealEntry` (Task 1).
- Produces: `data class MealAnalysisInput(mealId, inputType, text: String?, localImagePath: String?, date)`; `MealEntry.toAnalysisInput()`; `class MealAnalysisRunner(analyzer, repository, imageToBase64)` with `enum Outcome { SUCCESS, RETRY, FAILED }`, `data class RunResult(outcome, calories: Int? = null)`, `suspend run(input, attempt, maxAttempts): RunResult`, companion `MAX_ATTEMPTS = 4`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/meal/MealAnalysisRunnerTest.kt`:

```kotlin
package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealAnalysisRunnerTest {

    private class FakeAnalyzer(var result: MealAnalysisResult? = null, var error: String? = null) : MealAnalyzer {
        var callCount = 0
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            callCount++
            error?.let { throw MealAnalysisException(it) }
            return result!!
        }
    }

    private class RecordingRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        var completedId: String? = null
        var failedId: String? = null
        var failReason: String? = null
        override fun newMealId() = "x"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) { completedId = mealId }
        override fun failMeal(mealId: String, reason: String) { failedId = mealId; failReason = reason }
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun deleteMeal(mealId: String) {}
    }

    private val textInput = MealAnalysisInput("m1", "text", "2 eggs", null, "2026-06-25")
    private val imageInput = MealAnalysisInput("m2", "image", null, "/x/p.jpg", "2026-06-25")

    @Test
    fun `success completes meal and reports calories`() = runTest {
        val repo = RecordingRepo()
        val analyzer = FakeAnalyzer(MealAnalysisResult(listOf(MealItem("Egg", "2", 140, 12, 1, 10)), MealTotals(140, 12, 1, 10), false))
        val r = MealAnalysisRunner(analyzer, repo) { "BASE64" }.run(textInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.SUCCESS, r.outcome)
        assertEquals(140, r.calories)
        assertEquals("m1", repo.completedId)
    }

    @Test
    fun `transient failure under max attempts retries without failing the meal`() = runTest {
        val repo = RecordingRepo()
        val r = MealAnalysisRunner(FakeAnalyzer(error = "boom"), repo) { "BASE64" }.run(textInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.RETRY, r.outcome)
        assertNull(repo.failedId)
    }

    @Test
    fun `failure on last attempt marks the meal failed`() = runTest {
        val repo = RecordingRepo()
        val r = MealAnalysisRunner(FakeAnalyzer(error = "boom"), repo) { "BASE64" }.run(textInput, 3, 4)
        assertEquals(MealAnalysisRunner.Outcome.FAILED, r.outcome)
        assertEquals("m1", repo.failedId)
        assertEquals("boom", repo.failReason)
    }

    @Test
    fun `missing image file fails terminally without calling the analyzer`() = runTest {
        val repo = RecordingRepo()
        val analyzer = FakeAnalyzer()
        val r = MealAnalysisRunner(analyzer, repo) { null }.run(imageInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.FAILED, r.outcome)
        assertEquals("m2", repo.failedId)
        assertEquals(0, analyzer.callCount)
    }

    @Test
    fun `toAnalysisInput uses note as text for image meals`() {
        val entry = MealEntry(
            "m3", "2026-06-25", java.time.Instant.now(), "image", "ארוחה מנותחת AI",
            emptyList(), MealTotals(0, 0, 0, 0), localImagePath = "/x/p.jpg", note = "no sauce"
        )
        val input = entry.toAnalysisInput()
        assertEquals("no sauce", input.text)
        assertEquals("/x/p.jpg", input.localImagePath)
        assertEquals("image", input.inputType)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.meal.MealAnalysisRunnerTest"`
Expected: FAIL — `MealAnalysisInput`/`MealAnalysisRunner` unresolved.

- [ ] **Step 3: Create `MealAnalysisInput.kt`**

```kotlin
package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry

/** Everything WorkManager needs to (re)run one meal analysis. */
data class MealAnalysisInput(
    val mealId: String,
    val inputType: String, // "text" | "image"
    val text: String?,
    val localImagePath: String?,
    val date: String
)

/** Rebuild the input from a stored meal (used for manual "try again"). */
fun MealEntry.toAnalysisInput(): MealAnalysisInput = MealAnalysisInput(
    mealId = mealId,
    inputType = inputType,
    text = if (inputType == "image") note else description,
    localImagePath = localImagePath,
    date = date
)
```

- [ ] **Step 4: Create `MealAnalysisRunner.kt`**

```kotlin
package com.myhealthtracker.app.data.meal

/**
 * Runs a single meal-analysis attempt and updates the repository. Framework-free so it is
 * fully unit-testable; the WorkManager worker supplies the attempt counter and turns
 * [RunResult] into a WorkManager Result.
 */
class MealAnalysisRunner(
    private val analyzer: MealAnalyzer,
    private val repository: MealRepository,
    private val imageToBase64: (String) -> String?
) {
    enum class Outcome { SUCCESS, RETRY, FAILED }
    data class RunResult(val outcome: Outcome, val calories: Int? = null)

    suspend fun run(input: MealAnalysisInput, attempt: Int, maxAttempts: Int): RunResult {
        val base64: String? = if (input.inputType == "image") {
            val encoded = input.localImagePath?.let(imageToBase64)
            if (encoded == null) {
                repository.failMeal(input.mealId, "התמונה אינה זמינה")
                return RunResult(Outcome.FAILED)
            }
            encoded
        } else null

        return try {
            val result = analyzer.analyze(input.inputType, input.text, base64, input.date)
            repository.completeMeal(input.mealId, result.items, result.totals, result.recommendation, result.quality)
            RunResult(Outcome.SUCCESS, result.totals.calories)
        } catch (e: MealAnalysisException) {
            if (attempt + 1 < maxAttempts) {
                RunResult(Outcome.RETRY)
            } else {
                repository.failMeal(input.mealId, e.message ?: "הניתוח נכשל")
                RunResult(Outcome.FAILED)
            }
        }
    }

    companion object { const val MAX_ATTEMPTS = 4 }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.meal.MealAnalysisRunnerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisInput.kt app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisRunner.kt app/src/test/java/com/myhealthtracker/app/data/meal/MealAnalysisRunnerTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): analysis input DTO + pure retrying runner

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Background pipeline + app wiring (worker, scheduler, launcher, foreground, AppContainer, app init, sweep)

> Done before the ViewModel rewrite so `AppContainer.mealAnalysisLauncher` exists and the app initializes the pipeline. `MealAnalysisWorker` references `MealAnalysisNotifier` (Task 6) — create the notifier in this task too (the order below builds it first), or comment the two notifier lines and restore them in Task 6.

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/app/AppForegroundTracker.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/notification/MealAnalysisNotifier.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/notification/QuickActionsNotificationManager.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/sync/MealAnalysisScheduler.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/sync/MealAnalysisWorker.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisLauncher.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/app/AppForegroundTrackerTest.kt`

**Interfaces:**
- Produces: `AppForegroundTracker { onEnterForeground(); onEnterBackground(); isForeground() }`; `MealAnalysisNotifier { notifySuccess(ctx, mealId, calories); notifyFailure(ctx, mealId) }`; `QuickActionsNotificationManager.DEST_MEAL_RESULT`/`EXTRA_MEAL_ID`; `MealAnalysisScheduler.enqueue(ctx, input)` + `workName(mealId)`; `MealAnalysisWorker` (+ `toData`/`fromData`); `MealAnalysisLauncher` + `WorkManagerMealAnalysisLauncher`; `AppContainer.init(ctx)` + `AppContainer.mealAnalysisLauncher`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/app/AppForegroundTrackerTest.kt`:

```kotlin
package com.myhealthtracker.app.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForegroundTrackerTest {
    @Test
    fun `tracks enter and exit`() {
        AppForegroundTracker.onEnterBackground()
        assertFalse(AppForegroundTracker.isForeground())
        AppForegroundTracker.onEnterForeground()
        assertTrue(AppForegroundTracker.isForeground())
        AppForegroundTracker.onEnterBackground()
        assertFalse(AppForegroundTracker.isForeground())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.app.AppForegroundTrackerTest"`
Expected: FAIL — `AppForegroundTracker` unresolved.

- [ ] **Step 3: Create `AppForegroundTracker.kt`**

```kotlin
package com.myhealthtracker.app.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide "is the UI currently visible" flag, flipped from MainActivity.onStart/onStop.
 * The meal-analysis worker reads it to decide whether to post a completion notification
 * (only when the user is NOT looking at the app).
 */
object AppForegroundTracker {
    private val foreground = AtomicBoolean(false)
    fun onEnterForeground() = foreground.set(true)
    fun onEnterBackground() = foreground.set(false)
    fun isForeground(): Boolean = foreground.get()
}
```

- [ ] **Step 4: Run the tracker test to verify it passes**

Run: `./gradlew test --tests "com.myhealthtracker.app.app.AppForegroundTrackerTest"`
Expected: PASS.

- [ ] **Step 5: Add deep-link constants + `MealAnalysisNotifier.kt`**

In `QuickActionsNotificationManager.kt`, add to the constants block:

```kotlin
    const val DEST_MEAL_RESULT = "meal_result"
    const val EXTRA_MEAL_ID = "EXTRA_MEAL_ID"
```

Create `app/src/main/java/com/myhealthtracker/app/notification/MealAnalysisNotifier.kt`:

```kotlin
package com.myhealthtracker.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.R

/** One-shot notifications fired by MealAnalysisWorker when the app is in the background. */
object MealAnalysisNotifier {
    private const val CHANNEL_ID = "meal_analysis_channel"
    private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun notifySuccess(context: Context, mealId: String, calories: Int) =
        post(context, mealId, "המנה נותחה ✓", "$calories קלוריות — הקש לצפייה ועריכה")

    fun notifyFailure(context: Context, mealId: String) =
        post(context, mealId, "ניתוח המנה נכשל", "הקש כדי לנסות שוב או להזין ידנית")

    private fun post(context: Context, mealId: String, title: String, body: String) {
        val appContext = context.applicationContext
        createChannel(appContext)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(QuickActionsNotificationManager.EXTRA_NAVIGATE_TO, QuickActionsNotificationManager.DEST_MEAL_RESULT)
            putExtra(QuickActionsNotificationManager.EXTRA_MEAL_ID, mealId)
        }
        val pending = PendingIntent.getActivity(appContext, mealId.hashCode(), intent, PENDING_FLAGS)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(mealId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Meal analysis", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifies when a meal photo finishes analysis" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 6: Create `MealAnalysisScheduler.kt`**

```kotlin
package com.myhealthtracker.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import java.util.concurrent.TimeUnit

/** Enqueues a unique meal-analysis job per mealId. REPLACE lets a manual retry re-run it. */
object MealAnalysisScheduler {
    fun workName(mealId: String) = "mealAnalysis_$mealId"

    fun enqueue(context: Context, input: MealAnalysisInput) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<MealAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(MealAnalysisWorker.toData(input))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(input.mealId), ExistingWorkPolicy.REPLACE, request)
    }
}
```

- [ ] **Step 7: Create `MealAnalysisWorker.kt`**

```kotlin
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
```

- [ ] **Step 8: Create `MealAnalysisLauncher.kt`**

```kotlin
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
```

- [ ] **Step 9: Wire `AppContainer.kt`**

Add an app-context holder, an `init`, and the launcher (keep `initCelebrations` as-is; `init` calls it):

```kotlin
    @Volatile private var appContext: android.content.Context? = null

    /** Call once from Application.onCreate. Stores the app context and inits celebrations. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        initCelebrations(context)
    }

    val mealAnalysisLauncher: com.myhealthtracker.app.data.meal.MealAnalysisLauncher by lazy {
        val ctx = appContext ?: error("AppContainer.init(context) must be called before mealAnalysisLauncher")
        com.myhealthtracker.app.data.meal.WorkManagerMealAnalysisLauncher(ctx)
    }
```

- [ ] **Step 10: Wire `MyHealthApp.kt` — init + orphan sweep**

Replace `AppContainer.initCelebrations(this)` with `AppContainer.init(this)`, and add a one-shot orphan sweep:

```kotlin
        AppContainer.init(this)

        // Remove on-device meal images no longer referenced by any meal doc. Gate on a
        // NON-EMPTY snapshot for a signed-in user: the meals StateFlow starts empty and the
        // first emission may precede the Firestore load, so sweeping on empty would wrongly
        // delete valid images. Deletions also clean their own image at delete time (Task 9),
        // so skipping the sweep for a genuinely zero-meal user is safe.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            AppContainer.mealRepository.meals
                .filter { AppContainer.currentUid() != null && it.isNotEmpty() }
                .take(1)
                .collect { meals ->
                    val referenced = meals.mapNotNull { it.localImagePath }.toSet()
                    MealImageStore.sweepOrphans(MealImageStore.dir(this@MyHealthApp), referenced)
                }
        }
```

Add imports: `com.myhealthtracker.app.util.MealImageStore`, `kotlinx.coroutines.flow.filter`, `kotlinx.coroutines.flow.take`, `kotlinx.coroutines.launch`.

- [ ] **Step 11: Wire `MainActivity.kt` — foreground hooks**

Update `onStart` and add `onStop`:

```kotlin
    override fun onStart() {
        super.onStart()
        com.myhealthtracker.app.app.AppForegroundTracker.onEnterForeground()
        val uid = AppContainer.currentUid() ?: return
        lifecycleScope.launch { runCatching { AppContainer.activityRepository.touchLastActive(uid) } }
    }

    override fun onStop() {
        super.onStop()
        com.myhealthtracker.app.app.AppForegroundTracker.onEnterBackground()
    }
```

- [ ] **Step 12: Build + run the tracker test**

Run: `./gradlew assembleDebug` then `./gradlew test --tests "com.myhealthtracker.app.app.AppForegroundTrackerTest"`
Expected: BUILD SUCCESSFUL, then PASS.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/app/AppForegroundTracker.kt app/src/main/java/com/myhealthtracker/app/notification/MealAnalysisNotifier.kt app/src/main/java/com/myhealthtracker/app/notification/QuickActionsNotificationManager.kt app/src/main/java/com/myhealthtracker/app/sync/MealAnalysisScheduler.kt app/src/main/java/com/myhealthtracker/app/sync/MealAnalysisWorker.kt app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalysisLauncher.kt app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt app/src/main/java/com/myhealthtracker/app/MainActivity.kt app/src/test/java/com/myhealthtracker/app/app/AppForegroundTrackerTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): background analysis pipeline + app wiring (worker, launcher, foreground, notifier, sweep)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: AddMeal — ViewModel + Screen rewrite (create-pending, enqueue, dismiss)

> The ViewModel rewrite and the screen rewrite are one compile unit: the screen calls the new VM API, so both must change together (`./gradlew test` compiles the whole module). Implement both before running tests.

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`
- Modify: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

**Interfaces:**
- Consumes: `MealRepository` (Task 2), `MealAnalysisLauncher`/`MealAnalysisInput`/`AppContainer.mealAnalysisLauncher` (Tasks 4-5), `MealImageStore.saveFromUri` (Task 3), `MealTotals` (Task 1).
- Produces (on `AddMealViewModel`): constructor `(mealRepository, analysisLauncher, celebrationController)`; `sealed AddMealStep { InputSelection, ImagePreview, ManualFallback }`; flows `step`, `mealDescription`, `imageNote`, `pendingImagePath`, `errorMessage`, `manualCal/Protein/Carbs/Fat`, `closeScreen`; methods `onDescriptionChange`, `onImageNoteChange`, `onManual*Change`, `prepareImagePath(path)`, `seedPendingImagePathForTest(path)`, `analyzeText()`, `sendImageForAnalysis()`, `cancelImagePreview()`, `switchToManualFallback()`, `resetToInput()`, `saveManualMeal()`, `reset()`, `consumeClose()`.

- [ ] **Step 1: Rewrite `AddMealViewModel.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import com.myhealthtracker.app.data.meal.MealAnalysisLauncher
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object ImagePreview : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analysisLauncher: MealAnalysisLauncher = AppContainer.mealAnalysisLauncher,
    @Suppress("unused") private val celebrationController: CelebrationController = AppContainer.celebrationController
) : ViewModel() {

    private val _step = MutableStateFlow<AddMealStep>(AddMealStep.InputSelection)
    val step: StateFlow<AddMealStep> = _step.asStateFlow()
    private val _mealDescription = MutableStateFlow("")
    val mealDescription: StateFlow<String> = _mealDescription.asStateFlow()
    private val _imageNote = MutableStateFlow("")
    val imageNote: StateFlow<String> = _imageNote.asStateFlow()
    private val _pendingImagePath = MutableStateFlow<String?>(null)
    val pendingImagePath: StateFlow<String?> = _pendingImagePath.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _manualCal = MutableStateFlow(""); val manualCal: StateFlow<String> = _manualCal.asStateFlow()
    private val _manualProtein = MutableStateFlow(""); val manualProtein: StateFlow<String> = _manualProtein.asStateFlow()
    private val _manualCarbs = MutableStateFlow(""); val manualCarbs: StateFlow<String> = _manualCarbs.asStateFlow()
    private val _manualFat = MutableStateFlow(""); val manualFat: StateFlow<String> = _manualFat.asStateFlow()
    private val _closeScreen = MutableStateFlow(false)
    val closeScreen: StateFlow<Boolean> = _closeScreen.asStateFlow()

    fun onDescriptionChange(desc: String) { _mealDescription.value = desc; _errorMessage.value = null }
    fun onImageNoteChange(note: String) { _imageNote.value = note }
    fun onManualCalChange(v: String) { _manualCal.value = v }
    fun onManualProteinChange(v: String) { _manualProtein.value = v }
    fun onManualCarbsChange(v: String) { _manualCarbs.value = v }
    fun onManualFatChange(v: String) { _manualFat.value = v }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun prepareImagePath(path: String) {
        _errorMessage.value = null
        _imageNote.value = ""
        _pendingImagePath.value = path
        _step.value = AddMealStep.ImagePreview
    }

    internal fun seedPendingImagePathForTest(path: String) = prepareImagePath(path)

    fun analyzeText() {
        val desc = _mealDescription.value.trim()
        if (desc.isEmpty()) { _errorMessage.value = "אנא הקלד תיאור של הארוחה"; return }
        val mealId = mealRepository.newMealId()
        mealRepository.createPendingMeal(mealId, today(), "text", desc, null, null)
        analysisLauncher.launch(MealAnalysisInput(mealId, "text", desc, null, today()))
        _closeScreen.value = true
    }

    fun sendImageForAnalysis() {
        val path = _pendingImagePath.value ?: return
        val note = _imageNote.value.trim().ifEmpty { null }
        val description = note ?: "ארוחה מנותחת AI"
        val mealId = mealRepository.newMealId()
        mealRepository.createPendingMeal(mealId, today(), "image", description, note, path)
        analysisLauncher.launch(MealAnalysisInput(mealId, "image", note, path, today()))
        _closeScreen.value = true
    }

    fun cancelImagePreview() {
        _errorMessage.value = null
        _imageNote.value = ""
        _pendingImagePath.value = null
        _step.value = AddMealStep.InputSelection
    }

    fun switchToManualFallback() { _errorMessage.value = null; _step.value = AddMealStep.ManualFallback }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
        _imageNote.value = ""
        _pendingImagePath.value = null
    }

    fun saveManualMeal() {
        viewModelScope.launch {
            val cal = _manualCal.value.toIntOrNull() ?: 0
            if (cal <= 0) { _errorMessage.value = "הקלוריות חייבות להיות גדולות מ-0"; return@launch }
            val description = _mealDescription.value.ifEmpty { "ארוחה ידנית" }
            val protein = _manualProtein.value.toIntOrNull() ?: 0
            val carbs = _manualCarbs.value.toIntOrNull() ?: 0
            val fat = _manualFat.value.toIntOrNull() ?: 0
            val items = listOf(MealItem(description, "1 מנה", cal, protein, carbs, fat))
            mealRepository.addMeal(today(), "text", description, items, MealTotals(cal, protein, carbs, fat), null, null)
            _closeScreen.value = true
        }
    }

    fun reset() {
        _step.value = AddMealStep.InputSelection
        _mealDescription.value = ""
        _imageNote.value = ""
        _pendingImagePath.value = null
        _errorMessage.value = null
        _manualCal.value = ""; _manualProtein.value = ""; _manualCarbs.value = ""; _manualFat.value = ""
        _closeScreen.value = false
    }

    fun consumeClose() { _closeScreen.value = false }
}
```

- [ ] **Step 2: Rewrite `AddMealScreen.kt` wiring**

Apply these changes:

1. State collection — replace the result/loading/uri/base64 collectors with:

```kotlin
    val step by viewModel.step.collectAsState()
    val mealDescription by viewModel.mealDescription.collectAsState()
    val manualCal by viewModel.manualCal.collectAsState()
    val manualProtein by viewModel.manualProtein.collectAsState()
    val manualCarbs by viewModel.manualCarbs.collectAsState()
    val manualFat by viewModel.manualFat.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val closeScreen by viewModel.closeScreen.collectAsState()
    val pendingImagePath by viewModel.pendingImagePath.collectAsState()
    val imageNote by viewModel.imageNote.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
```

2. Dismiss effect:

```kotlin
    LaunchedEffect(closeScreen) {
        if (closeScreen) { onDismiss(); viewModel.reset() }
    }
```

3. Camera + gallery launchers persist to disk then preview:

```kotlin
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) scope.launch {
            com.myhealthtracker.app.util.MealImageStore.saveFromUri(context.applicationContext, uri)
                ?.let { viewModel.prepareImagePath(it) }
        }
        if (!success) pendingCameraFile?.delete()
        pendingCameraFile = null; pendingCameraUri = null
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            com.myhealthtracker.app.util.MealImageStore.saveFromUri(context, uri)
                ?.let { viewModel.prepareImagePath(it) }
        }
    }
```

(Keep the `permissionLauncher` as-is.)

4. `when (step)` now has only three branches — `ImagePreview`, `InputSelection`, `ManualFallback`. Remove the `AddMealStep.Loading` and `AddMealStep.ResultState` branches entirely. Pass `imagePath = pendingImagePath` to `ImagePreviewContent`, and change the manual branch's save to `onSaveClick = { viewModel.saveManualMeal() }`.

5. `ImagePreviewContent` signature: replace `imageUri: android.net.Uri?` with `imagePath: String?` and render with Coil from a `File`:

```kotlin
            if (imagePath != null) {
                AsyncImage(
                    model = java.io.File(imagePath),
                    contentDescription = "תצוגה מקדימה של הארוחה",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                )
            }
```

6. Delete `LoadingContent`, `ResultStateContent`, the `dashedBorder` helper if only `ResultStateContent` used it, and the `AddMealScreenPreviewLoading`/`AddMealScreenPreviewResult` previews. (The result editor is recreated in Task 8 as `MealResultContent`.) Update the `ImagePreview` preview to pass `imagePath = null`.

- [ ] **Step 3: Replace `AddMealViewModelTest.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import com.myhealthtracker.app.data.meal.MealAnalysisLauncher
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMealViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        var nextId = "mid-1"
        val pending = mutableListOf<Triple<String, String, String?>>() // id, inputType, localImagePath
        val added = mutableListOf<MealEntry>()
        override fun newMealId() = nextId
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {
            pending.add(Triple(mealId, inputType, localImagePath))
        }
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {
            added.add(MealEntry("aid", date, java.time.Instant.now(), inputType, description, items, totals))
        }
        override fun deleteMeal(mealId: String) {}
    }

    private class FakeLauncher : MealAnalysisLauncher {
        val launched = mutableListOf<MealAnalysisInput>()
        override fun launch(input: MealAnalysisInput) { launched.add(input) }
    }

    private fun vm(repo: MealRepository = FakeRepo(), launcher: MealAnalysisLauncher = FakeLauncher()) =
        AddMealViewModel(repo, launcher, CelebrationController(InMemoryCelebrationStore()))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analyzeText creates a pending meal, enqueues analysis, and requests close`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.onDescriptionChange("2 eggs"); vm.analyzeText()
        assertEquals(1, repo.pending.size); assertEquals("text", repo.pending.first().second)
        assertEquals(1, launcher.launched.size); assertEquals("2 eggs", launcher.launched.first().text)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `empty text is rejected and nothing is queued`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.onDescriptionChange("   "); vm.analyzeText()
        assertEquals(0, repo.pending.size); assertEquals(0, launcher.launched.size)
        assertFalse(vm.closeScreen.value); assertTrue(vm.errorMessage.value != null)
    }

    @Test
    fun `sendImageForAnalysis queues an image meal with note as text and the local path`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.seedPendingImagePathForTest("/data/x/p.jpg")
        vm.onImageNoteChange("תפוח אורגני"); vm.sendImageForAnalysis()
        assertEquals("image", repo.pending.first().second)
        assertEquals("/data/x/p.jpg", repo.pending.first().third)
        assertEquals("תפוח אורגני", launcher.launched.first().text)
        assertEquals("/data/x/p.jpg", launcher.launched.first().localImagePath)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `sendImageForAnalysis with no pending path is a no-op`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.sendImageForAnalysis()
        assertEquals(0, repo.pending.size); assertEquals(0, launcher.launched.size)
    }

    @Test
    fun `manual save writes a complete meal directly and requests close`() = runTest(dispatcher) {
        val repo = FakeRepo(); val vm = vm(repo, FakeLauncher())
        vm.switchToManualFallback(); vm.onManualCalChange("500"); vm.saveManualMeal(); advanceUntilIdle()
        assertEquals(1, repo.added.size); assertEquals(500, repo.added.first().totals.calories)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `manual save rejects non-positive calories`() = runTest(dispatcher) {
        val repo = FakeRepo(); val vm = vm(repo, FakeLauncher())
        vm.switchToManualFallback(); vm.onManualCalChange("0"); vm.saveManualMeal(); advanceUntilIdle()
        assertEquals(0, repo.added.size); assertEquals("הקלוריות חייבות להיות גדולות מ-0", vm.errorMessage.value)
    }

    @Test
    fun `reset clears close flag and pending state`() = runTest(dispatcher) {
        val vm = vm()
        vm.onDescriptionChange("x"); vm.analyzeText(); assertTrue(vm.closeScreen.value)
        vm.reset()
        assertFalse(vm.closeScreen.value); assertEquals("", vm.mealDescription.value)
        assertEquals(AddMealStep.InputSelection, vm.step.value)
    }
}
```

- [ ] **Step 4: Run the test + build**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"` then `./gradlew assembleDebug`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Install (`./gradlew installDebug`). Add a text meal → screen closes immediately, no crash, a meal appears in the journal (badge wiring lands in Task 10). Pick a gallery image → preview shows the photo → send → screen closes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): AddMeal queues background analysis + dismisses; image saved to disk

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: FoodViewModel — complete-only totals, failed banner count, unseen pick

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/ui/meal/UnseenMealRouter.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/UnseenMealRouterTest.kt`

**Interfaces:**
- Consumes: `MealEntry`/`MealStatus`/`MealTotals` (Task 1).
- Produces: `pickUnseenMealToShow(meals): MealEntry?`, `completeMealTotals(meals): MealTotals`, `failedMealCount(meals): Int`; `FoodState.failedMealCount: Int`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/ui/meal/UnseenMealRouterTest.kt`:

```kotlin
package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.model.MealTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UnseenMealRouterTest {
    private fun meal(id: String, status: String, seen: Boolean, at: Instant, cal: Int = 0) =
        MealEntry(id, "2026-06-25", at, "text", "x", emptyList(), MealTotals(cal, 0, 0, 0), status = status, seen = seen)

    @Test
    fun `picks the most recent complete unseen meal`() {
        val older = meal("a", MealStatus.COMPLETE, false, Instant.ofEpochSecond(100))
        val newer = meal("b", MealStatus.COMPLETE, false, Instant.ofEpochSecond(200))
        assertEquals("b", pickUnseenMealToShow(listOf(older, newer))?.mealId)
    }

    @Test
    fun `ignores seen, analyzing, and failed meals`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(300)),
            meal("b", MealStatus.ANALYZING, false, Instant.ofEpochSecond(400)),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(500))
        )
        assertNull(pickUnseenMealToShow(meals))
    }

    @Test
    fun `complete-only totals exclude analyzing and failed`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(1), cal = 200),
            meal("b", MealStatus.ANALYZING, false, Instant.ofEpochSecond(2), cal = 999),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(3), cal = 999)
        )
        assertEquals(200, completeMealTotals(meals).calories)
    }

    @Test
    fun `failed count counts only failed meals`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(1)),
            meal("b", MealStatus.FAILED, false, Instant.ofEpochSecond(2)),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(3))
        )
        assertEquals(2, failedMealCount(meals))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.UnseenMealRouterTest"`
Expected: FAIL — functions unresolved.

- [ ] **Step 3: Create `UnseenMealRouter.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.model.MealTotals

/** The most recent completed meal the user has not seen yet, or null. */
fun pickUnseenMealToShow(meals: List<MealEntry>): MealEntry? =
    meals.filter { it.status == MealStatus.COMPLETE && !it.seen }.maxByOrNull { it.loggedAt }

/** Sum macros over completed meals only; in-progress/failed meals contribute nothing. */
fun completeMealTotals(meals: List<MealEntry>): MealTotals {
    val complete = meals.filter { it.status == MealStatus.COMPLETE }
    return MealTotals(
        calories = complete.sumOf { it.totals.calories },
        proteinG = complete.sumOf { it.totals.proteinG },
        carbsG = complete.sumOf { it.totals.carbsG },
        fatG = complete.sumOf { it.totals.fatG }
    )
}

fun failedMealCount(meals: List<MealEntry>): Int = meals.count { it.status == MealStatus.FAILED }
```

- [ ] **Step 4: Wire into `FoodViewModel.kt`**

Add `val failedMealCount: Int = 0` to `FoodState`. In the `combine` block replace the manual totals sums and add the banner count (computed over **all** loaded meals so it works on any selected day):

```kotlin
        val dailyMeals = allMeals.filter { it.date == dateStr }
        val waterIntake = waterMap[dateStr] ?: 0
        val dayTotals = completeMealTotals(dailyMeals)
        val advice = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.NUTRITION).text

        FoodState(
            selectedDate = date,
            meals = dailyMeals,
            waterIntakeMl = waterIntake,
            totals = dayTotals,
            aiAdvice = advice,
            isRefreshing = isRefreshing,
            failedMealCount = failedMealCount(allMeals)
        )
```

Add imports `com.myhealthtracker.app.ui.meal.completeMealTotals` and `com.myhealthtracker.app.ui.meal.failedMealCount`.

- [ ] **Step 5: Run tests to verify they pass + build**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.UnseenMealRouterTest"` then `./gradlew assembleDebug`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/UnseenMealRouter.kt app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/meal/UnseenMealRouterTest.kt
git commit -m "$(cat <<'EOF'
feat(food): complete-only totals, failed-meal count, unseen picker

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Reusable result editor + MealEditViewModel + EditMeal screen

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealResultContent.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealEditViewModel.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealEditScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/NavigationKeys.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/MealEditViewModelTest.kt`

**Interfaces:**
- Consumes: `MealRepository.updateMeal`/`markMealSeen` (Task 2), `MealEntry`/`MealTotals.fromItems` (Task 1), `CelebrationController`/`CelebrationRules.mealQuality` (existing).
- Produces: `MealResultContent(imagePath, recognizedItems, excludedIndices, lowConfidence, recommendation, quality, errorMessage, onItemUpdate, onToggleRemoved, onItemAdd, onSaveClick, modifier)`; `MealEditViewModel(mealRepository, celebrationController)` with `load(meal)`, `updateItem`, `toggleItemRemoved`, `addItem`, `save()`, `celebrateQuality()`, flows `recognizedItems`/`excludedIndices`/`recommendation`/`quality`/`imagePath`/`description`/`errorMessage`/`saved`; `@Serializable data class EditMeal(val mealId: String) : NavKey`; `MealEditScreen(meal, celebrateOnOpen, onDismiss, modifier)`.

- [ ] **Step 1: Add the nav key**

In `NavigationKeys.kt`:

```kotlin
@Serializable data class EditMeal(val mealId: String) : NavKey
```

- [ ] **Step 2: Create `MealResultContent.kt`**

Recreate the former `ResultStateContent` layout (the editable items list, macro summary cards, info notice, quality/recommendation cards, add-item card, and a Save button) as a public composable. Two deltas from the old version: (a) accept `imagePath: String?` and render it as the first `LazyColumn` item when non-null; (b) drop the "הזנה ידנית" link and its `onManualClick` param.

```kotlin
package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
// ...plus the same imports the former ResultStateContent used (icons, shapes, theme colors).

@Composable
fun MealResultContent(
    imagePath: String?,
    recognizedItems: List<MealItem>,
    excludedIndices: Set<Int>,
    lowConfidence: Boolean,
    recommendation: String?,
    quality: MealQuality?,
    errorMessage: String?,
    onItemUpdate: (Int, MealItem) -> Unit,
    onToggleRemoved: (Int) -> Unit,
    onItemAdd: (MealItem) -> Int,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Body identical to the former AddMealScreen.ResultStateContent, with this added as the
    // FIRST LazyColumn item when imagePath != null:
    //   item {
    //     AsyncImage(
    //       model = java.io.File(imagePath),
    //       contentDescription = "תמונת הארוחה",
    //       contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    //       modifier = Modifier.fillMaxWidth().height(200.dp)
    //         .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
    //     )
    //   }
    // Totals derive from active (non-excluded) items exactly as before. No manual-entry link.
}
```

> The full body is the existing `ResultStateContent` you deleted in Task 6 — reproduce it verbatim minus the manual link, plus the image item. Keep the private `dashedBorder` helper here if `MealResultContent` uses it.

- [ ] **Step 3: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/ui/meal/MealEditViewModelTest.kt`:

```kotlin
package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        val updated = mutableListOf<Triple<String, List<MealItem>, MealTotals>>()
        val seen = mutableListOf<String>()
        override fun newMealId() = "x"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) { seen.add(mealId) }
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) { updated.add(Triple(mealId, items, totals)) }
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun deleteMeal(mealId: String) {}
    }

    private fun meal() = MealEntry(
        "m1", "2026-06-25", java.time.Instant.now(), "image", "salad",
        listOf(MealItem("A", "1", 100, 10, 5, 2), MealItem("B", "1", 250, 20, 30, 8)),
        MealTotals(350, 30, 35, 10), status = MealStatus.COMPLETE, seen = false,
        quality = MealQuality(processedScore = 1, insulinImpact = "low")
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load marks the meal seen and seeds items`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = MealEditViewModel(repo, CelebrationController(InMemoryCelebrationStore()))
        vm.load(meal()); advanceUntilIdle()
        assertEquals(listOf("m1"), repo.seen)
        assertEquals(2, vm.recognizedItems.value.size)
    }

    @Test
    fun `save excludes removed items, recomputes totals, and updates the meal`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = MealEditViewModel(repo, CelebrationController(InMemoryCelebrationStore()))
        vm.load(meal()); advanceUntilIdle()
        vm.toggleItemRemoved(1) // drop B
        vm.save(); advanceUntilIdle()
        assertEquals(1, repo.updated.size)
        val (id, items, totals) = repo.updated.first()
        assertEquals("m1", id); assertEquals(1, items.size); assertEquals(100, totals.calories)
        assertTrue(vm.saved.value)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.MealEditViewModelTest"`
Expected: FAIL — `MealEditViewModel` unresolved.

- [ ] **Step 5: Create `MealEditViewModel.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the existing-meal result/editor screen (detail "edit", analysis-complete
 * notification, and unseen-meal interception). On load it marks the meal seen so it stops
 * popping; [celebrateQuality] is fired by the screen when surfaced via interception.
 */
class MealEditViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val celebrationController: CelebrationController = AppContainer.celebrationController
) : ViewModel() {

    private var mealId: String? = null
    private var date: String = ""

    private val _description = MutableStateFlow(""); val description: StateFlow<String> = _description.asStateFlow()
    private val _items = MutableStateFlow<List<MealItem>>(emptyList()); val recognizedItems: StateFlow<List<MealItem>> = _items.asStateFlow()
    private val _excluded = MutableStateFlow<Set<Int>>(emptySet()); val excludedIndices: StateFlow<Set<Int>> = _excluded.asStateFlow()
    private val _recommendation = MutableStateFlow<String?>(null); val recommendation: StateFlow<String?> = _recommendation.asStateFlow()
    private val _quality = MutableStateFlow<MealQuality?>(null); val quality: StateFlow<MealQuality?> = _quality.asStateFlow()
    private val _imagePath = MutableStateFlow<String?>(null); val imagePath: StateFlow<String?> = _imagePath.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null); val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _saved = MutableStateFlow(false); val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun load(meal: MealEntry) {
        mealId = meal.mealId; date = meal.date
        _description.value = meal.description
        _items.value = meal.items
        _excluded.value = emptySet()
        _recommendation.value = meal.recommendation
        _quality.value = meal.quality
        _imagePath.value = meal.localImagePath
        _saved.value = false
        if (!meal.seen) mealRepository.markMealSeen(meal.mealId)
    }

    fun updateItem(index: Int, item: MealItem) {
        val list = _items.value.toMutableList()
        if (index in list.indices) { list[index] = item; _items.value = list }
    }

    fun toggleItemRemoved(index: Int) {
        if (index !in _items.value.indices) return
        val cur = _excluded.value
        _excluded.value = if (index in cur) cur - index else cur + index
    }

    fun addItem(item: MealItem): Int {
        val list = _items.value.toMutableList(); list.add(item); _items.value = list
        return list.size - 1
    }

    fun save() {
        val id = mealId ?: return
        val active = _items.value.filterIndexed { i, _ -> i !in _excluded.value }
        if (active.isEmpty()) { _errorMessage.value = "כל הפריטים הוסרו"; return }
        mealRepository.updateMeal(id, _description.value, active, MealTotals.fromItems(active))
        _saved.value = true
    }

    fun celebrateQuality() {
        celebrationController.celebrateNow(CelebrationRules.mealQuality(_quality.value, date))
    }
}
```

- [ ] **Step 6: Create `MealEditScreen.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.data.model.MealEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(
    meal: MealEntry,
    celebrateOnOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: MealEditViewModel = viewModel()
    LaunchedEffect(meal.mealId) {
        viewModel.load(meal)
        if (celebrateOnOpen) viewModel.celebrateQuality()
    }
    val items by viewModel.recognizedItems.collectAsState()
    val excluded by viewModel.excludedIndices.collectAsState()
    val recommendation by viewModel.recommendation.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val imagePath by viewModel.imagePath.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) { if (saved) onDismiss() }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("פרטי הארוחה", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "סגור") } }
            )
        }
    ) { padding ->
        MealResultContent(
            imagePath = imagePath,
            recognizedItems = items,
            excludedIndices = excluded,
            lowConfidence = false,
            recommendation = recommendation,
            quality = quality,
            errorMessage = error,
            onItemUpdate = { i, it -> viewModel.updateItem(i, it) },
            onToggleRemoved = { viewModel.toggleItemRemoved(it) },
            onItemAdd = { viewModel.addItem(it) },
            onSaveClick = { viewModel.save() },
            modifier = Modifier.padding(padding)
        )
    }
}
```

- [ ] **Step 7: Run tests + build**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.MealEditViewModelTest"` then `./gradlew assembleDebug`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/MealResultContent.kt app/src/main/java/com/myhealthtracker/app/ui/meal/MealEditViewModel.kt app/src/main/java/com/myhealthtracker/app/ui/meal/MealEditScreen.kt app/src/main/java/com/myhealthtracker/app/NavigationKeys.kt app/src/test/java/com/myhealthtracker/app/ui/meal/MealEditViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(meal): reusable result editor + MealEdit screen for saved meals

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: FoodScreen — status badges, thumbnail, failure banner, photo + edit + recovery

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt`

**Interfaces:**
- Consumes: `FoodState.failedMealCount` (Task 7), `MealStatus`/`MealEntry.localImagePath`/`status`/`failureReason` (Task 1), `MealImageStore.delete` (Task 3), `MealAnalysisScheduler.enqueue` + `toAnalysisInput` (Tasks 4-5), `AppContainer.mealRepository` (Task 2).
- Produces: `FoodScreen`/`FoodContent` gain `onEditMeal: (MealEntry) -> Unit = {}` threaded to `MealDetailSheet`.

> UI glue — verify by build + manual run.

- [ ] **Step 1: Failure banner**

In `FoodContent`, render above `AnimatedContent` (so it persists across day changes) when `state.failedMealCount > 0`:

```kotlin
if (state.failedMealCount > 0) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                "יש ${state.failedMealCount} מנות שלא נותחו — הקש על המנה האדומה ביומן כדי לנסות שוב",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

Add `import androidx.compose.material.icons.filled.Warning`.

- [ ] **Step 2: Status badge helper + meal-card thumbnail/badge**

Add at file scope:

```kotlin
@Composable
private fun StatusBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
```

In the `itemsIndexed(state.meals)` card, render the photo when present (and not still analyzing) instead of the emoji avatar:

```kotlin
val path = meal.localImagePath
if (path != null && meal.status != MealStatus.ANALYZING) {
    AsyncImage(
        model = java.io.File(path),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
    )
} else {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) { Text(getMealEmoji(meal.description), fontSize = 28.sp) }
}
```

Under the title, add the badge:

```kotlin
when (meal.status) {
    MealStatus.ANALYZING -> StatusBadge("⏳ מנתח…", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    MealStatus.FAILED -> StatusBadge("⚠️ נכשל — הקש לתיקון", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    else -> {}
}
```

Add imports `coil.compose.AsyncImage`, `com.myhealthtracker.app.data.model.MealStatus`.

- [ ] **Step 3: Card tap routing + recovery dialog state**

Add `var recoveryMeal by remember { mutableStateOf<MealEntry?>(null) }` near `selectedMeal`. Change the card `clickable`:

```kotlin
.clickable {
    when (meal.status) {
        MealStatus.FAILED -> recoveryMeal = meal
        MealStatus.ANALYZING -> { /* no-op while analyzing */ }
        else -> { selectedMealTitle = mealTitle; selectedMeal = meal }
    }
}
```

- [ ] **Step 4: Recovery dialog**

After the `MealDetailSheet` block:

```kotlin
recoveryMeal?.let { meal ->
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { recoveryMeal = null },
        title = { Text("המנה לא נותחה") },
        text = { Text(meal.failureReason ?: "ניתוח המנה נכשל.") },
        confirmButton = {
            TextButton(onClick = {
                AppContainer.mealRepository.retryMeal(meal.mealId)
                MealAnalysisScheduler.enqueue(context, meal.toAnalysisInput())
                recoveryMeal = null
            }) { Text("נסה שוב") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    MealImageStore.delete(meal.localImagePath)
                    AppContainer.mealRepository.deleteMeal(meal.mealId)
                    recoveryMeal = null
                }) { Text("מחק") }
                TextButton(onClick = { recoveryMeal = null }) { Text("סגור") }
            }
        }
    )
}
```

Add imports: `androidx.compose.ui.platform.LocalContext`, `com.myhealthtracker.app.di.AppContainer`, `com.myhealthtracker.app.sync.MealAnalysisScheduler`, `com.myhealthtracker.app.data.meal.toAnalysisInput`, `com.myhealthtracker.app.util.MealImageStore`.

> "הזן ידנית" recovery is covered by deleting and re-adding via the manual flow; the dialog's "נסה שוב"/"מחק" map to the spec's retry/manual/delete (manual = delete then use the normal manual-entry screen).

- [ ] **Step 5: Photo + edit in `MealDetailSheet`**

Add `onEdit: (MealEntry) -> Unit` to `MealDetailSheet`; thread `onEditMeal` from `FoodScreen`/`FoodContent` (new param, default `{}`). Show the photo after the description:

```kotlin
meal.localImagePath?.let { path ->
    AsyncImage(
        model = java.io.File(path),
        contentDescription = "תמונת הארוחה",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
    )
}
```

Add an "ערוך" `TextButton` in the sheet header row → `onEdit(meal); onDismiss()`.

- [ ] **Step 6: Build + manual verification**

Run: `./gradlew assembleDebug`
Install and verify: analyzing card shows "⏳ מנתח…"; on completion flips to photo + normal card; force a failure (airplane mode until retries exhaust) → red card + top banner; tapping the red card → recovery dialog → "נסה שוב" re-queues; detail sheet shows the photo and an "ערוך" button.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt
git commit -m "$(cat <<'EOF'
feat(food): status badges, photo thumbnails, failure banner + recovery dialog

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Navigation — meal-result deep link + unseen interception with quick-action exception

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/Navigation.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `EditMeal` key + `MealEditScreen` (Task 8), `pickUnseenMealToShow` (Task 7), `QuickActionsNotificationManager.DEST_MEAL_RESULT`/`EXTRA_MEAL_ID`/`DEST_ADD_MEAL`/`DEST_ADD_WORKOUT` (Task 5), `AppContainer.mealRepository` (Task 2), `MealStatus` (Task 1).

> Wiring — the decision function `pickUnseenMealToShow` is unit-tested (Task 7). Verify wiring by build + manual run.

- [ ] **Step 1: Carry the mealId + suppression flag on the deep link**

Replace the deep-link state and `applyPendingDestination` in `MainNavigation`:

```kotlin
  var pendingDestination by remember { mutableStateOf<String?>(null) }
  var pendingMealId by remember { mutableStateOf<String?>(null) }
  // True for this launch when it came from a quick-action add button; suppresses the
  // unseen-meal interception so the user lands on the screen they asked for.
  var suppressUnseenInterception by remember { mutableStateOf(false) }

  fun applyPendingDestination() {
    when (pendingDestination) {
      QuickActionsNotificationManager.DEST_ADD_MEAL -> backStack.add(AddMeal)
      QuickActionsNotificationManager.DEST_ADD_WORKOUT -> backStack.add(AddWorkout)
      QuickActionsNotificationManager.DEST_MEAL_RESULT -> pendingMealId?.let { backStack.add(EditMeal(it)) }
    }
    pendingDestination = null
    pendingMealId = null
  }

  LaunchedEffect(intent) {
    val destination = intent?.getStringExtra(QuickActionsNotificationManager.EXTRA_NAVIGATE_TO)
    if (destination != null) {
      pendingDestination = destination
      pendingMealId = intent.getStringExtra(QuickActionsNotificationManager.EXTRA_MEAL_ID)
      suppressUnseenInterception =
        destination == QuickActionsNotificationManager.DEST_ADD_MEAL ||
        destination == QuickActionsNotificationManager.DEST_ADD_WORKOUT
      onIntentHandled()
      if (AppContainer.currentUid() != null) applyPendingDestination()
    }
  }
```

- [ ] **Step 2: Unseen interception on Dashboard**

Add a `LaunchedEffect` that pops an unseen completed meal when on the Dashboard and not launched via a quick-action add:

```kotlin
  LaunchedEffect(Unit) {
    if (suppressUnseenInterception) return@LaunchedEffect
    AppContainer.mealRepository.meals.collect { meals ->
      if (backStack.lastOrNull() != Dashboard) return@collect
      val unseen = pickUnseenMealToShow(meals) ?: return@collect
      // Mark all currently-unseen completed meals seen so they don't pop again.
      meals.filter { it.status == com.myhealthtracker.app.data.model.MealStatus.COMPLETE && !it.seen }
        .forEach { AppContainer.mealRepository.markMealSeen(it.mealId) }
      backStack.add(EditMeal(unseen.mealId))
    }
  }
```

Add imports: `com.myhealthtracker.app.ui.meal.pickUnseenMealToShow`, `androidx.compose.runtime.collectAsState`.

- [ ] **Step 3: Add the `EditMeal` entry**

In `entryProvider`:

```kotlin
        entry<EditMeal> { key ->
          val meals by AppContainer.mealRepository.meals.collectAsState()
          val meal = meals.firstOrNull { it.mealId == key.mealId }
          if (meal != null) {
            com.myhealthtracker.app.ui.meal.MealEditScreen(
              meal = meal,
              celebrateOnOpen = !meal.seen, // unseen → first surfacing celebrates; manual re-edit does not
              onDismiss = { backStack.removeLastOrNull() }
            )
          } else {
            LaunchedEffect(Unit) { backStack.removeLastOrNull() }
          }
        }
```

> `celebrateOnOpen = !meal.seen` is read from the snapshot before `MealEditViewModel.load` marks it seen, so the celebration fires exactly once for a freshly surfaced meal.

- [ ] **Step 4: Thread the detail-sheet edit hook**

Give `MainScreen` an `onNavigateToEditMeal: (String) -> Unit` param and pass it to `FoodScreen` as `onEditMeal = { onNavigateToEditMeal(it.mealId) }`. In the `Dashboard` entry, pass `onNavigateToEditMeal = { backStack.add(EditMeal(it)) }`.

- [ ] **Step 5: Build + manual verification**

Run: `./gradlew assembleDebug`
Verify:
- Background-complete a meal, reopen from the launcher → its result screen pops, celebration plays for a great/good meal, and reopening again does **not** re-pop it.
- Tap the completion notification → opens that meal's result screen.
- Tap a quick-action "הוספת ארוחה" notification button while an unseen meal exists → lands on AddMeal, **not** the unseen result.
- Detail sheet "ערוך" → opens the editor and saves edits.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/Navigation.kt app/src/main/java/com/myhealthtracker/app/ui/main/MainScreen.kt
git commit -m "$(cat <<'EOF'
feat(nav): meal-result deep link + unseen-result interception with quick-action exception

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Docs — iron-rule reword, HLD, changelog

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/HLD-health-tracker.md`
- Modify: `docs/CHANGELOG.md`

> Docs only — no tests.

- [ ] **Step 1: Reword iron rule #2 in `CLAUDE.md`**

Replace:

```
2. **תמונות אוכל לא נשמרות.** נשלחות לניתוח, התוצאה נשמרת, התמונה נזרקת.
```

with:

```
2. **תמונות אוכל לעולם לא עולות/נשמרות בענן.** ניתן לשמור אותן **מקומית במכשיר בלבד** (לצפייה חוזרת ולניסיון ניתוח חוזר). הניתוח נשלח ל-Cloud Function; התמונה לא עוזבת את המכשיר. רק נתיב הקובץ המקומי נשמר במסמך ה-Firestore.
```

- [ ] **Step 2: Update the meal data-model note in `CLAUDE.md`**

Change the `meals/{mealId}` line to include the new fields and add a clarifying bullet:

```
├── meals/{mealId}    : { date, loggedAt, inputType, description, items[], totals, aiModel, status, seen, localImagePath?, note?, failureReason? }
```

Add: `- meals.status ∈ "analyzing"|"complete"|"failed" — הניתוח רץ ב-WorkManager (רקע + ניסיונות חוזרים). רק מנות "complete" נספרות בסיכומים היומיים ובמנגנון התובנות. localImagePath מקומי בלבד.`

- [ ] **Step 3: Mirror in `docs/HLD-health-tracker.md`**

Add the local-only-photo clarification and a short paragraph describing the background analysis pipeline (create-pending → WorkManager → complete/failed → notification/unseen interception), plus the `status`/`seen`/`localImagePath`/`note`/`failureReason` fields, in the meals/privacy section.

- [ ] **Step 4: Add a CHANGELOG entry**

Prepend under the latest/unreleased section:

```
- Meal analysis is now background + auto-save: meals persist immediately as "analyzing",
  run through WorkManager with retries, and update in place to complete/failed. Photos are
  stored on-device (never uploaded) and shown on saved meals. Completed-but-unseen meals pop
  their result screen (with celebration) on next app entry; failures surface as a Food-screen
  banner with retry/delete. Quick-action notification entries bypass the interception.
```

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs/HLD-health-tracker.md docs/CHANGELOG.md
git commit -m "$(cat <<'EOF'
docs(meal): reword photo iron-rule to local-only; document analysis pipeline

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Final verification

- [ ] Run `./gradlew test` — all unit tests pass.
- [ ] Run `./gradlew assembleDebug` — build succeeds.
- [ ] Manual end-to-end on a device/emulator:
  - Text meal → card shows "מנתח…" → flips to complete; daily totals update only after complete.
  - Photo meal → preview → send → screen closes → card shows the photo after analysis; detail shows the photo; "ערוך" edits and persists.
  - Leave the app during analysis → completion notification → tapping opens the meal.
  - Reopen with an unseen completed meal → result screen pops + celebration (great/good); does not re-pop afterward.
  - Quick-action "הוספת ארוחה" notification while an unseen meal exists → AddMeal opens, interception skipped.
  - Force failures (offline through all retries) → red card + top banner → recovery dialog (נסה שוב / מחק).
  - Delete a meal → its on-device image is removed; orphan sweep on next launch leaves no stray files.
```
