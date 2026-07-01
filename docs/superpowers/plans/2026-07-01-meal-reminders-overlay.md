# Meal Reminders (Floating Overlay) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remind the user to photograph/log a meal with a floating popup over other apps at each meal time, unless that meal was already logged.

**Architecture:** A daily `AlarmManager` (inexact) alarm per meal slot fires a `BroadcastReceiver`, which reads today's meals, asks a pure `MealReminderPolicy` whether the meal is still un-logged, and if so starts a `Service` that hosts the existing `MealReminderOverlay` composable in a `WindowManager` overlay window. Settings (times, toggles) live in a device-local DataStore and drive scheduling.

**Tech Stack:** Kotlin, Jetpack Compose, AlarmManager, WindowManager `TYPE_APPLICATION_OVERLAY`, DataStore Preferences, Coroutines/Flow. JUnit4 + kotlinx-coroutines-test for unit tests.

## Global Constraints

- **No new dependencies** — the waiter animation is native Compose over `R.drawable.waiter`; no `lottie-compose`.
- **Inexact alarms only** — `AlarmManager.setAndAllowWhileIdle`, never exact alarms (no `SCHEDULE_EXACT_ALARM`).
- **Permissions added:** only `SYSTEM_ALERT_WINDOW` and `RECEIVE_BOOT_COMPLETED`.
- **Default reminder times:** `07:00` breakfast, `12:00` lunch, `19:00` dinner. Labels are Hebrew: `ארוחת בוקר`, `ארוחת צהריים`, `ארוחת ערב`.
- **User-facing strings in Hebrew.** Code, comments, identifiers in English.
- **No blocking calls on the main thread** — DataStore/meal reads run in coroutines / `goAsync`.
- **No AI, no medical advice** — the popup is a neutral logging nudge.
- **Test command:** `./gradlew test` (single class: `./gradlew test --tests "FQCN"`).
- **Only meals with `date == today` and `status == MealStatus.COMPLETE` count** for suppression.

---

### Task 1: Reminder settings model, codec & store

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettings.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettingsStore.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/reminders/ReminderSettingsTest.kt`

**Interfaces:**
- Produces: `data class ReminderSlot(val time: LocalTime, val mealLabel: String, val enabled: Boolean = true)`; `data class ReminderSettings(val masterEnabled: Boolean = true, val slots: List<ReminderSlot>)` with `ReminderSettings.DEFAULT`; `object ReminderSettingsCodec { fun encodeSlots(List<ReminderSlot>): String; fun decodeSlots(String): List<ReminderSlot> }`; `interface ReminderSettingsStore { val settings: Flow<ReminderSettings>; suspend fun update(new: ReminderSettings) }`; `class InMemoryReminderSettingsStore`; `class DataStoreReminderSettingsStore(context)`; `AppContainer.reminderSettingsStore: ReminderSettingsStore`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/reminders/ReminderSettingsTest.kt`:

```kotlin
package com.myhealthtracker.app.data.reminders

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsTest {

    @Test
    fun `default has three enabled meal slots at 7 12 19`() {
        val d = ReminderSettings.DEFAULT
        assertEquals(3, d.slots.size)
        assertEquals(LocalTime.of(7, 0), d.slots[0].time)
        assertEquals(LocalTime.of(12, 0), d.slots[1].time)
        assertEquals(LocalTime.of(19, 0), d.slots[2].time)
        assertEquals(listOf("ארוחת בוקר", "ארוחת צהריים", "ארוחת ערב"), d.slots.map { it.mealLabel })
        assertEquals(true, d.masterEnabled)
    }

    @Test
    fun `codec round-trips slots including a disabled one`() {
        val slots = listOf(
            ReminderSlot(LocalTime.of(9, 30), "ארוחת בוקר", true),
            ReminderSlot(LocalTime.of(13, 0), "ארוחת צהריים", false),
        )
        val decoded = ReminderSettingsCodec.decodeSlots(ReminderSettingsCodec.encodeSlots(slots))
        assertEquals(slots, decoded)
    }

    @Test
    fun `in-memory store returns updated settings`() = runTest {
        val store = InMemoryReminderSettingsStore()
        val updated = ReminderSettings.DEFAULT.copy(masterEnabled = false)
        store.update(updated)
        assertEquals(updated, store.settings.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.reminders.ReminderSettingsTest"`
Expected: FAIL — unresolved references (`ReminderSettings`, `ReminderSlot`, `ReminderSettingsCodec`, `InMemoryReminderSettingsStore`).

- [ ] **Step 3: Create the model + codec**

Create `app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettings.kt`:

```kotlin
package com.myhealthtracker.app.data.reminders

import java.time.LocalTime

/** One meal-time reminder. `time` is the moment the popup fires; `mealLabel` is the Hebrew meal name. */
data class ReminderSlot(
    val time: LocalTime,
    val mealLabel: String,
    val enabled: Boolean = true
)

/** All reminder settings. Device-local only; never synced to Firestore. */
data class ReminderSettings(
    val masterEnabled: Boolean = true,
    val slots: List<ReminderSlot>
) {
    companion object {
        val DEFAULT = ReminderSettings(
            masterEnabled = true,
            slots = listOf(
                ReminderSlot(LocalTime.of(7, 0), "ארוחת בוקר", true),
                ReminderSlot(LocalTime.of(12, 0), "ארוחת צהריים", true),
                ReminderSlot(LocalTime.of(19, 0), "ארוחת ערב", true),
            )
        )
    }
}

/** Serializes slots to a single ordered string for DataStore (labels never contain '|' or ';'). */
object ReminderSettingsCodec {
    fun encodeSlots(slots: List<ReminderSlot>): String =
        slots.joinToString(";") { "${it.time}|${it.mealLabel}|${it.enabled}" }

    fun decodeSlots(encoded: String): List<ReminderSlot> =
        if (encoded.isBlank()) emptyList()
        else encoded.split(";").map { part ->
            val (t, label, en) = part.split("|")
            ReminderSlot(LocalTime.parse(t), label, en.toBoolean())
        }
}
```

- [ ] **Step 4: Create the store**

Create `app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettingsStore.kt`:

```kotlin
package com.myhealthtracker.app.data.reminders

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

interface ReminderSettingsStore {
    val settings: Flow<ReminderSettings>
    suspend fun update(new: ReminderSettings)
}

/** Test/fallback implementation with no persistence. */
class InMemoryReminderSettingsStore(
    initial: ReminderSettings = ReminderSettings.DEFAULT
) : ReminderSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<ReminderSettings> = state.asStateFlow()
    override suspend fun update(new: ReminderSettings) { state.value = new }
}

private val Context.reminderDataStore by preferencesDataStore(name = "reminders")

/** Local, per-device persistence. Same pattern as DataStoreCelebrationStore. */
class DataStoreReminderSettingsStore(context: Context) : ReminderSettingsStore {
    private val appContext = context.applicationContext
    private val masterKey = booleanPreferencesKey("master_enabled")
    private val slotsKey = stringPreferencesKey("slots")

    override val settings: Flow<ReminderSettings> = appContext.reminderDataStore.data.map { prefs ->
        val slots = prefs[slotsKey]?.let { ReminderSettingsCodec.decodeSlots(it) }
            ?: ReminderSettings.DEFAULT.slots
        ReminderSettings(masterEnabled = prefs[masterKey] ?: true, slots = slots)
    }

    override suspend fun update(new: ReminderSettings) {
        appContext.reminderDataStore.edit { prefs ->
            prefs[masterKey] = new.masterEnabled
            prefs[slotsKey] = ReminderSettingsCodec.encodeSlots(new.slots)
        }
    }
}
```

- [ ] **Step 5: Expose it from AppContainer**

In `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`, add near the celebration wiring (after the `initCelebrations` block). Add the import `import com.myhealthtracker.app.data.reminders.DataStoreReminderSettingsStore` and `import com.myhealthtracker.app.data.reminders.InMemoryReminderSettingsStore` and `import com.myhealthtracker.app.data.reminders.ReminderSettingsStore`, then:

```kotlin
    // Meal reminders. Device-local settings; swapped to DataStore-backed in init().
    @Volatile
    private var _reminderSettingsStore: ReminderSettingsStore? = null

    val reminderSettingsStore: ReminderSettingsStore
        get() = _reminderSettingsStore
            ?: InMemoryReminderSettingsStore().also { _reminderSettingsStore = it }
```

In the existing `fun init(context: Context)` body, add after `initCelebrations(context)`:

```kotlin
        _reminderSettingsStore = DataStoreReminderSettingsStore(context.applicationContext)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.reminders.ReminderSettingsTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettings.kt app/src/main/java/com/myhealthtracker/app/data/reminders/ReminderSettingsStore.kt app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt app/src/test/java/com/myhealthtracker/app/data/reminders/ReminderSettingsTest.kt
git commit -m "feat(reminders): settings model, codec, and device-local store"
```

---

### Task 2: MealReminderPolicy (pure suppression logic)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/reminders/MealReminderPolicy.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/reminders/MealReminderPolicyTest.kt`

**Interfaces:**
- Consumes: `ReminderSettings`, `ReminderSlot` (Task 1).
- Produces: `object MealReminderPolicy { fun shouldRemind(slotIndex: Int, settings: ReminderSettings, todaysMealTimes: List<LocalTime>, now: LocalTime): Boolean }`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/reminders/MealReminderPolicyTest.kt`:

```kotlin
package com.myhealthtracker.app.data.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealReminderPolicyTest {

    private val settings = ReminderSettings.DEFAULT // 07:00, 12:00, 19:00 all enabled

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `fires when no meals logged`() {
        assertTrue(MealReminderPolicy.shouldRemind(0, settings, emptyList(), t(7)))
    }

    @Test
    fun `breakfast at 8 does not suppress the 12h lunch reminder`() {
        // lunch slot 1: prev enabled 07:00, midpoint 09:30, window [09:30, 12:00]
        assertTrue(MealReminderPolicy.shouldRemind(1, settings, listOf(t(8)), t(12)))
    }

    @Test
    fun `lunch logged at 11h30 suppresses the 12h lunch reminder`() {
        assertFalse(MealReminderPolicy.shouldRemind(1, settings, listOf(t(11, 30)), t(12)))
    }

    @Test
    fun `meal exactly on the midpoint boundary suppresses`() {
        assertFalse(MealReminderPolicy.shouldRemind(1, settings, listOf(t(9, 30)), t(12)))
    }

    @Test
    fun `meal one minute before the midpoint does not suppress`() {
        assertTrue(MealReminderPolicy.shouldRemind(1, settings, listOf(t(9, 29)), t(12)))
    }

    @Test
    fun `first slot window starts at midnight`() {
        // breakfast slot 0: window [00:00, 07:00]; a 06:30 log suppresses
        assertFalse(MealReminderPolicy.shouldRemind(0, settings, listOf(t(6, 30)), t(7)))
    }

    @Test
    fun `disabled slot never fires`() {
        val s = settings.copy(slots = settings.slots.mapIndexed { i, slot ->
            if (i == 1) slot.copy(enabled = false) else slot
        })
        assertFalse(MealReminderPolicy.shouldRemind(1, s, emptyList(), t(12)))
    }

    @Test
    fun `master disabled never fires`() {
        assertFalse(MealReminderPolicy.shouldRemind(0, settings.copy(masterEnabled = false), emptyList(), t(7)))
    }

    @Test
    fun `disabled middle slot widens the next slot window to the previous enabled slot`() {
        // Disable lunch (12:00). Dinner slot 2: prev enabled is breakfast 07:00,
        // midpoint(07:00, 19:00) = 13:00, window [13:00, now].
        val s = settings.copy(slots = settings.slots.mapIndexed { i, slot ->
            if (i == 1) slot.copy(enabled = false) else slot
        })
        // A 12:30 meal is before 13:00 -> does NOT suppress dinner.
        assertTrue(MealReminderPolicy.shouldRemind(2, s, listOf(t(12, 30)), t(19)))
        // A 14:00 meal is inside -> suppresses.
        assertFalse(MealReminderPolicy.shouldRemind(2, s, listOf(t(14)), t(19)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.reminders.MealReminderPolicyTest"`
Expected: FAIL — `MealReminderPolicy` unresolved.

- [ ] **Step 3: Implement the policy**

Create `app/src/main/java/com/myhealthtracker/app/data/reminders/MealReminderPolicy.kt`:

```kotlin
package com.myhealthtracker.app.data.reminders

import java.time.LocalTime

/**
 * Decides whether the meal-time reminder for a slot should fire. The reminder prompts the
 * user to photograph the meal, so it fires UNLESS that meal was already logged. "Already
 * logged" = a complete meal in the suppression window [Sᵢ, now], where Sᵢ is the midpoint
 * between the previous enabled slot and this slot (midnight for the first slot). The
 * midpoint boundary keeps meals independent: logging breakfast does not suppress lunch.
 */
object MealReminderPolicy {

    fun shouldRemind(
        slotIndex: Int,
        settings: ReminderSettings,
        todaysMealTimes: List<LocalTime>,
        now: LocalTime
    ): Boolean {
        if (!settings.masterEnabled) return false
        val slot = settings.slots.getOrNull(slotIndex) ?: return false
        if (!slot.enabled) return false

        val prevEnabled = settings.slots.take(slotIndex).lastOrNull { it.enabled }
        val lowerBound =
            if (prevEnabled == null) LocalTime.MIDNIGHT
            else midpoint(prevEnabled.time, slot.time)

        val alreadyLogged = todaysMealTimes.any { it >= lowerBound && it <= now }
        return !alreadyLogged
    }

    private fun midpoint(a: LocalTime, b: LocalTime): LocalTime {
        val mid = (a.toSecondOfDay() + b.toSecondOfDay()) / 2
        return LocalTime.ofSecondOfDay(mid.toLong())
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.myhealthtracker.app.data.reminders.MealReminderPolicyTest"`
Expected: PASS (10 assertions across 9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/reminders/MealReminderPolicy.kt app/src/test/java/com/myhealthtracker/app/data/reminders/MealReminderPolicyTest.kt
git commit -m "feat(reminders): pure meal-time suppression policy with tests"
```

---

### Task 3: Alarm scheduling & receivers

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/notification/ReminderScheduler.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/notification/ReminderAlarmReceiver.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/notification/ReminderBootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AppContainer.reminderSettingsStore`, `AppContainer.currentUid()`, `AppContainer.mealRepository`, `ReminderSettings`, `MealReminderPolicy` (Tasks 1–2), `MealStatus.COMPLETE`, `ReminderOverlayService.start(context, mealLabel, slotIndex)` (Task 4, referenced by name).
- Produces: `object ReminderScheduler { const val EXTRA_SLOT_INDEX; fun armAll(context, settings); fun armNextOccurrence(context, slotIndex, time); fun snooze(context, slotIndex, minutes=30); fun cancelAll(context) }`; `class ReminderAlarmReceiver : BroadcastReceiver`; `class ReminderBootReceiver : BroadcastReceiver`.

> No unit tests — this is Android-framework plumbing (`AlarmManager`, `BroadcastReceiver`). Verified by build + the manual verification plan in the spec. `ReminderOverlayService` is created in Task 4; this task references it by name, so the module first compiles at the end of Task 4. Build after Task 4.

- [ ] **Step 1: Create the scheduler**

Create `app/src/main/java/com/myhealthtracker/app/notification/ReminderScheduler.kt`:

```kotlin
package com.myhealthtracker.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myhealthtracker.app.data.reminders.ReminderSettings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules one inexact daily alarm per enabled meal slot. Styled after HealthSyncScheduler.
 * Inexact (setAndAllowWhileIdle) so no SCHEDULE_EXACT_ALARM permission is needed.
 */
object ReminderScheduler {
    const val EXTRA_SLOT_INDEX = "slot_index"
    private const val ACTION = "com.myhealthtracker.app.MEAL_REMINDER"
    private const val REQUEST_BASE = 7000
    private const val MAX_SLOTS = 8

    fun armAll(context: Context, settings: ReminderSettings) {
        if (!settings.masterEnabled) { cancelAll(context); return }
        settings.slots.forEachIndexed { index, slot ->
            if (slot.enabled) armNextOccurrence(context, index, slot.time)
            else cancelSlot(context, index)
        }
    }

    fun armNextOccurrence(context: Context, slotIndex: Int, time: LocalTime) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, slotIndex, triggerAt)
    }

    fun snooze(context: Context, slotIndex: Int, minutes: Long = 30) {
        setAlarm(context, slotIndex, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun cancelAll(context: Context) {
        for (i in 0 until MAX_SLOTS) cancelSlot(context, i)
    }

    private fun setAlarm(context: Context, slotIndex: Int, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, slotIndex))
    }

    private fun cancelSlot(context: Context, slotIndex: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, slotIndex))
    }

    private fun pendingIntent(context: Context, slotIndex: Int): PendingIntent {
        val intent = Intent(context.applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_BASE + slotIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

- [ ] **Step 2: Create the alarm receiver**

Create `app/src/main/java/com/myhealthtracker/app/notification/ReminderAlarmReceiver.kt`:

```kotlin
package com.myhealthtracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.reminders.MealReminderPolicy
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fired at a slot's meal time. Reads today's meals, asks MealReminderPolicy whether the
 * meal is still un-logged, and if so shows the floating overlay. Always re-arms the next
 * daily occurrence so the schedule keeps running.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotIndex = intent.getIntExtra(ReminderScheduler.EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppContainer.reminderSettingsStore.settings.first()
                val slot = settings.slots.getOrNull(slotIndex)

                // Re-arm the next daily occurrence regardless of the outcome below.
                if (slot != null && slot.enabled && settings.masterEnabled) {
                    ReminderScheduler.armNextOccurrence(appContext, slotIndex, slot.time)
                }

                if (slot == null || AppContainer.currentUid() == null) return@launch
                if (!Settings.canDrawOverlays(appContext)) return@launch

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                // meals is a StateFlow seeded empty and loaded async; wait briefly for the
                // Firestore (offline-cached) load. A genuinely zero-meal day times out and
                // we proceed with an empty list (which correctly triggers a reminder).
                val meals = withTimeoutOrNull(4000) {
                    AppContainer.mealRepository.meals.first { it.isNotEmpty() }
                } ?: emptyList()

                val zone = ZoneId.systemDefault()
                val mealTimes = meals
                    .filter { it.date == today && it.status == MealStatus.COMPLETE }
                    .map { it.loggedAt.atZone(zone).toLocalTime() }

                if (MealReminderPolicy.shouldRemind(slotIndex, settings, mealTimes, LocalTime.now())) {
                    ReminderOverlayService.start(appContext, slot.mealLabel, slotIndex)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 3: Create the boot receiver**

Create `app/src/main/java/com/myhealthtracker/app/notification/ReminderBootReceiver.kt`:

```kotlin
package com.myhealthtracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms all reminder alarms after a device reboot (alarms don't survive reboot). */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppContainer.reminderSettingsStore.settings.first()
                ReminderScheduler.armAll(appContext, settings)
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Register permissions and receivers in the manifest**

In `app/src/main/AndroidManifest.xml`, add these `<uses-permission>` lines next to the existing permissions:

```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Inside `<application>`, next to the existing `<receiver ... WaterLoggingReceiver ...>`, add:

```xml
        <receiver
            android:name=".notification.ReminderAlarmReceiver"
            android:exported="false" />

        <receiver
            android:name=".notification.ReminderBootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/notification/ReminderScheduler.kt app/src/main/java/com/myhealthtracker/app/notification/ReminderAlarmReceiver.kt app/src/main/java/com/myhealthtracker/app/notification/ReminderBootReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(reminders): inexact alarm scheduler + alarm/boot receivers"
```

---

### Task 4: Overlay service + parametrized composable

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealReminderOverlay.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/notification/ReminderOverlayService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MealReminderOverlay(isVisible, title, body, onLogMeal, onRemindLater, onDismiss)`; `ReminderScheduler.snooze`; `QuickActionsNotificationManager.EXTRA_NAVIGATE_TO` / `DEST_ADD_MEAL`; `MyHealthTrackerTheme`; `MainActivity`.
- Produces: `object`-style `companion` `ReminderOverlayService.start(context, mealLabel, slotIndex)`.

> No unit tests — WindowManager/Service integration, verified by build + manual plan. After this task the module compiles; run a build.

- [ ] **Step 1: Add `title`/`body` parameters to the existing composable**

In `app/src/main/java/com/myhealthtracker/app/ui/meal/MealReminderOverlay.kt`, change the function signature from:

```kotlin
@Composable
fun MealReminderOverlay(
    isVisible: Boolean,
    onLogMeal: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit
) {
```

to:

```kotlin
@Composable
fun MealReminderOverlay(
    isVisible: Boolean,
    title: String = "היי, לא שכחת משהו?",
    body: String = "הגיע הזמן לעדכן את הארוחה האחרונה שלך! המלצר שלנו כבר מחכה.",
    onLogMeal: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit
) {
```

Then replace the two hard-coded `Text(...)` calls inside the card (the `"היי, לא שכחת משהו?"` title and the `"הגיע הזמן לעדכן..."` body) so they use the parameters:

```kotlin
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = body,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
```

- [ ] **Step 2: Create the overlay service**

Create `app/src/main/java/com/myhealthtracker/app/notification/ReminderOverlayService.kt`:

```kotlin
package com.myhealthtracker.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.myhealthtracker.app.MainActivity
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import com.myhealthtracker.app.ui.meal.MealReminderOverlay

/**
 * Hosts the floating reminder popup in a WindowManager overlay window. Requires
 * SYSTEM_ALERT_WINDOW (also grants the Android 12+ background start exemption). Provides
 * the lifecycle/savedstate/viewmodel owners a ComposeView needs to render off-Activity.
 */
class ReminderOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var overlayView: View? = null
    private val visible = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || overlayView != null) {
            if (overlayView == null) stopSelf()
            return START_NOT_STICKY
        }
        val mealLabel = intent?.getStringExtra(EXTRA_MEAL_LABEL) ?: ""
        val slotIndex = intent?.getIntExtra(EXTRA_SLOT_INDEX, -1) ?: -1
        showOverlay(mealLabel, slotIndex)
        return START_NOT_STICKY
    }

    private fun showOverlay(mealLabel: String, slotIndex: Int) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ReminderOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ReminderOverlayService)
            setViewTreeViewModelStoreOwner(this@ReminderOverlayService)
            setContent {
                MyHealthTrackerTheme {
                    MealReminderOverlay(
                        isVisible = visible.value,
                        title = if (mealLabel.isNotBlank()) "📸 זמן לתעד את $mealLabel" else "היי, לא שכחת משהו?",
                        onLogMeal = { openAddMeal(); dismiss() },
                        onRemindLater = {
                            if (slotIndex >= 0) ReminderScheduler.snooze(applicationContext, slotIndex)
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
        overlayView = composeView
        wm.addView(composeView, params)
        // Flip visible after attach so AnimatedVisibility plays the slide-in.
        composeView.post { visible.value = true }
    }

    private fun openAddMeal() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                QuickActionsNotificationManager.EXTRA_NAVIGATE_TO,
                QuickActionsNotificationManager.DEST_ADD_MEAL
            )
        })
    }

    private fun dismiss() {
        visible.value = false
        // Let the exit animation finish before removing the window.
        overlayView?.postDelayed({ removeAndStop() }, 350)
    }

    private fun removeAndStop() {
        removeView()
        stopSelf()
    }

    private fun removeView() {
        overlayView?.let {
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeView()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    companion object {
        private const val EXTRA_MEAL_LABEL = "meal_label"
        private const val EXTRA_SLOT_INDEX = "slot_index"

        fun start(context: Context, mealLabel: String, slotIndex: Int) {
            context.startService(
                Intent(context, ReminderOverlayService::class.java).apply {
                    putExtra(EXTRA_MEAL_LABEL, mealLabel)
                    putExtra(EXTRA_SLOT_INDEX, slotIndex)
                }
            )
        }
    }
}
```

- [ ] **Step 3: Register the service in the manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>` (next to the receivers from Task 3), add:

```xml
        <service
            android:name=".notification.ReminderOverlayService"
            android:exported="false" />
```

- [ ] **Step 4: Build the whole module (compiles Tasks 3 + 4 together)**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any unresolved references (e.g. confirm `MyHealthTrackerTheme` import path `com.myhealthtracker.app.theme.MyHealthTrackerTheme`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/MealReminderOverlay.kt app/src/main/java/com/myhealthtracker/app/notification/ReminderOverlayService.kt app/src/main/AndroidManifest.xml
git commit -m "feat(reminders): WindowManager overlay service hosting the waiter popup"
```

---

### Task 5: Reminder settings screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsViewModel.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsScreen.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReminderSettingsStore`, `ReminderSettings`, `ReminderSlot` (Task 1); `ReminderScheduler.armAll` (Task 3).
- Produces: `class ReminderSettingsViewModel(store, onChanged: (ReminderSettings) -> Unit)` with `val settings: StateFlow<ReminderSettings>`, `fun setMasterEnabled(Boolean)`, `fun setSlotEnabled(Int, Boolean)`, `fun setSlotTime(Int, LocalTime)`; `@Composable fun ReminderSettingsScreen(onBack, onGrantOverlay, modifier)`.

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsViewModelTest.kt`:

```kotlin
package com.myhealthtracker.app.ui.reminders

import com.myhealthtracker.app.data.reminders.InMemoryReminderSettingsStore
import com.myhealthtracker.app.data.reminders.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `setSlotTime persists and reschedules`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        var rescheduledWith: ReminderSettings? = null
        val vm = ReminderSettingsViewModel(store) { rescheduledWith = it }

        vm.setSlotTime(1, LocalTime.of(13, 0))
        testScheduler.advanceUntilIdle()

        assertEquals(LocalTime.of(13, 0), store.settings.first().slots[1].time)
        assertEquals(LocalTime.of(13, 0), rescheduledWith?.slots?.get(1)?.time)
    }

    @Test
    fun `setMasterEnabled false persists`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        val vm = ReminderSettingsViewModel(store) {}

        vm.setMasterEnabled(false)
        testScheduler.advanceUntilIdle()

        assertFalse(store.settings.first().masterEnabled)
    }

    @Test
    fun `setSlotEnabled toggles the target slot only`() = runTest(dispatcher) {
        val store = InMemoryReminderSettingsStore()
        val vm = ReminderSettingsViewModel(store) {}

        vm.setSlotEnabled(0, false)
        testScheduler.advanceUntilIdle()

        val slots = store.settings.first().slots
        assertFalse(slots[0].enabled)
        assertTrue(slots[1].enabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.reminders.ReminderSettingsViewModelTest"`
Expected: FAIL — `ReminderSettingsViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel**

Create `app/src/main/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsViewModel.kt`:

```kotlin
package com.myhealthtracker.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.reminders.ReminderSettings
import com.myhealthtracker.app.data.reminders.ReminderSettingsStore
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Edits reminder settings. Each change persists to the store and calls [onChanged] so the
 * caller can re-arm the AlarmManager schedule (kept out of the ViewModel for unit-testability).
 */
class ReminderSettingsViewModel(
    private val store: ReminderSettingsStore = AppContainer.reminderSettingsStore,
    private val onChanged: (ReminderSettings) -> Unit
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings.DEFAULT)

    fun setMasterEnabled(enabled: Boolean) =
        persist(settings.value.copy(masterEnabled = enabled))

    fun setSlotEnabled(index: Int, enabled: Boolean) = persist(
        settings.value.copy(slots = settings.value.slots.mapIndexed { i, s ->
            if (i == index) s.copy(enabled = enabled) else s
        })
    )

    fun setSlotTime(index: Int, time: LocalTime) = persist(
        settings.value.copy(slots = settings.value.slots.mapIndexed { i, s ->
            if (i == index) s.copy(time = time) else s
        })
    )

    private fun persist(new: ReminderSettings) {
        viewModelScope.launch {
            store.update(new)
            onChanged(new)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.reminders.ReminderSettingsViewModelTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Implement the screen**

Create `app/src/main/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsScreen.kt`:

```kotlin
package com.myhealthtracker.app.ui.reminders

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.ReminderScheduler
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: ReminderSettingsViewModel = viewModel {
        ReminderSettingsViewModel(AppContainer.reminderSettingsStore) { settings ->
            ReminderScheduler.armAll(context, settings)
        }
    }
    val settings by vm.settings.collectAsState()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "תזכורות ארוחה",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "תזכורת קופצת בזמן הארוחה כדי שתזכור לצלם ולתעד אותה",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("הפעל תזכורות", fontWeight = FontWeight.Bold)
            Switch(checked = settings.masterEnabled, onCheckedChange = { vm.setMasterEnabled(it) })
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        settings.slots.forEachIndexed { index, slot ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(slot.mealLabel, modifier = Modifier.weight(1f))
                Text(
                    text = slot.time.format(fmt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, m -> vm.setSlotTime(index, java.time.LocalTime.of(h, m)) },
                                slot.time.hour, slot.time.minute, true
                            ).show()
                        }
                )
                Switch(checked = slot.enabled, onCheckedChange = { vm.setSlotEnabled(index, it) })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Button(onClick = onGrantOverlay, modifier = Modifier.padding(top = 8.dp)) {
            Text("אפשר הצגה מעל אפליקציות אחרות")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("חזרה")
        }
    }
}
```

- [ ] **Step 6: Build to confirm the screen compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/reminders/ app/src/test/java/com/myhealthtracker/app/ui/reminders/
git commit -m "feat(reminders): settings screen + view model with tests"
```

---

### Task 6: Wire startup, sign-out, navigation & profile entry

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/NavigationKeys.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/Navigation.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/auth/AuthViewModel.kt`

**Interfaces:**
- Consumes: `ReminderScheduler.armAll/cancelAll` (Task 3); `AppContainer.reminderSettingsStore` (Task 1); `ReminderSettingsScreen` (Task 5).
- Produces: a `ReminderSettingsRoute` nav key + route; `onNavigateToReminderSettings` passed to `ProfileScreen` from the `entry<Profile>` block (a new profile row). Note: `ProfileScreen` is its own nav route reached via `onNavigateToProfile`, so nothing threads through `MainScreen`.

> No new unit tests — pure wiring; verified by build + the manual verification plan.

- [ ] **Step 1: Add the nav key**

In `app/src/main/java/com/myhealthtracker/app/NavigationKeys.kt`, add (note: the class is named `ReminderSettings`, distinct from the data class `com.myhealthtracker.app.data.reminders.ReminderSettings`):

```kotlin
@Serializable data object ReminderSettingsRoute : NavKey
```

- [ ] **Step 2: Arm reminders on startup, cancel on sign-out**

In `app/src/main/java/com/myhealthtracker/app/ui/auth/AuthViewModel.kt`, add the import `import com.myhealthtracker.app.notification.ReminderScheduler` and, inside `signOut`, after the `cancelUniqueWork(...)` line:

```kotlin
        ReminderScheduler.cancelAll(context)
```

In `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`, add imports:

```kotlin
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.ReminderScheduler
import kotlinx.coroutines.flow.first
```

(`AppContainer` is already imported.) Then, inside `setContent { ... }` after the existing quick-actions `LaunchedEffect(authUser, quickActionsEnabled) { ... }`, add:

```kotlin
            LaunchedEffect(authUser) {
                if (authUser != null) {
                    val settings = AppContainer.reminderSettingsStore.settings.first()
                    ReminderScheduler.armAll(context, settings)
                }
            }
```

- [ ] **Step 3: Add the ReminderSettings route to navigation**

In `app/src/main/java/com/myhealthtracker/app/Navigation.kt`, add the imports:

```kotlin
import android.net.Uri
import android.provider.Settings as AndroidSettings
import com.myhealthtracker.app.ui.reminders.ReminderSettingsScreen
```

Add a new `entry` block next to `entry<AddMeal>`:

```kotlin
        entry<ReminderSettingsRoute> {
          val context = LocalContext.current
          ReminderSettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            onGrantOverlay = {
              context.startActivity(
                Intent(
                  AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}")
                )
              )
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
```

- [ ] **Step 4: Add the entry-point parameter + row in ProfileScreen**

`ProfileScreen` is its own nav route (`entry<Profile>`), reached from the dashboard via
`onNavigateToProfile`. Wire the new hook there — nothing goes through `MainScreen`.

In `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`, add a parameter to the `ProfileScreen` composable signature:

```kotlin
    onNavigateToReminderSettings: () -> Unit = {},
```

Then, next to the "התראת פעולות מהירות" row (after its trailing `HorizontalDivider`), add a clickable navigation row:

```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToReminderSettings() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "תזכורות ארוחה",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "תזכורת קופצת בזמן הארוחה כדי לצלם ולתעד",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
```

Ensure `androidx.compose.foundation.clickable` is imported (add `import androidx.compose.foundation.clickable` if missing).

- [ ] **Step 5: Pass the hook into the ProfileScreen call**

In `Navigation.kt`, the only place that renders `ProfileScreen` is the `entry<Profile>` block. Add to that `ProfileScreen(...)` call:

```kotlin
            onNavigateToReminderSettings = { backStack.add(ReminderSettingsRoute) },
```

- [ ] **Step 6: Build and run the full unit suite**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all existing tests plus the new reminder tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/NavigationKeys.kt app/src/main/java/com/myhealthtracker/app/Navigation.kt app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt app/src/main/java/com/myhealthtracker/app/MainActivity.kt app/src/main/java/com/myhealthtracker/app/ui/auth/AuthViewModel.kt
git commit -m "feat(reminders): wire scheduling on startup/sign-out + settings navigation"
```

---

## Manual Verification (after Task 6)

Follow the spec's manual verification plan (`docs/superpowers/specs/2026-07-01-meal-reminders-overlay-design.md`):
1. Grant overlay permission from the reminder settings screen.
2. Set a slot a minute ahead with no meal logged, open another app → floating waiter popup appears.
3. Log a meal just before the slot time → no popup.
4. Buttons: "רשום ארוחה" opens Add-Meal; "תזכיר עוד 30 דק'" re-fires ~30 min later (self-suppresses if logged meanwhile); "ביטול" dismisses.
5. Reboot → reminders still fire.
6. Sign out → no popups.
