# Meal Reminders (Floating Overlay) Design

This document details the design for **meal-logging reminders** that surface as a
floating popup **over other apps** (not a drawer notification) at configurable times of
day, when no meal has been logged in the relevant meal window. The popup shows a playful
Lottie animation of a waiter rising from the bottom holding a tray, with three actions:
**log a meal**, **snooze 30 minutes**, or **dismiss**.

The core problem being solved: the user forgets to log meals, and forgetting means the
app is usually *not open* — so an in-app-only reminder can't help. The reminder must
reach the user while the app is closed.

## User Review Required

> [!IMPORTANT]
> - **Overlay permission**: The floating popup requires `SYSTEM_ALERT_WINDOW`
>   ("display over other apps"). The user grants it once via system settings. This is a
>   deliberate, personal-app choice (the app is not published to Play Store, where Google
>   discourages this permission). If the permission is missing when a reminder fires, we
>   skip silently — never crash.
> - **Inexact alarms**: We use `AlarmManager` **inexact** alarms
>   (`setAndAllowWhileIdle`), so no `SCHEDULE_EXACT_ALARM` permission is needed. A meal
>   reminder does not need second-level precision; it may fire a few minutes late in Doze.
> - **Default times**: `10:00` / `14:00` / `20:00` (breakfast / lunch / dinner windows).
>   These are more meaningful for "you missed a meal" than the originally-mentioned
>   `7:00 / 12:00 / 19:00` (a 07:00 window is almost always empty and would nag every
>   morning). All three times are editable in the new settings screen, including reverting
>   to 7/12/19.
> - **Lottie asset**: The waiter animation is produced separately (a design agent, per the
>   prepared prompt) as a vector Lottie JSON with a transparent background. This spec
>   assumes the asset is dropped into `res/raw/`; the three buttons and text are native
>   Compose, **not** part of the Lottie.

## Window Semantics

Reminders are an ordered list of "slots", each `{ time, mealLabel, enabled }`. For slot
`i` at time `Tᵢ`, the checked window is **`[Tᵢ₋₁, Tᵢ)`** (the first slot's window starts
at midnight). When slot `i` fires at `Tᵢ`, if **no `complete` meal** has a `loggedAt`
within that window, the popup is shown for `mealLabelᵢ`.

Defaults:
- `10:00` → window `[00:00, 10:00)` → "לא רשמת ארוחת בוקר 🍳"
- `14:00` → window `[10:00, 14:00)` → "לא רשמת ארוחת צהריים 🥗"
- `20:00` → window `[14:00, 20:00)` → "לא רשמת ארוחת ערב 🍽️"

Times compare against the device's local time zone. Only meals with `date == today` and
`status == complete` count; `analyzing`/`failed` meals are ignored.

## Architecture

Small, isolated units. The decision logic is pure Kotlin (no Android), so it is fully
unit-testable; the Android pieces (scheduler, receiver, service) stay thin.

| Unit | Responsibility | Depends on | Tested |
|------|----------------|------------|--------|
| `ReminderSettings` + `ReminderSettingsStore` | Persist master toggle + 3 slots (time/label/enabled) in DataStore | DataStore | ✅ InMemory |
| `MealWindowChecker` (pure) | Given a slot index, settings, and today's meal timestamps → is the window missed? | — | ✅ unit |
| `ReminderScheduler` | Wrap `AlarmManager`: (re)arm each enabled slot's next occurrence; snooze; cancel all | AlarmManager | — |
| `ReminderAlarmReceiver` | Fires at a slot time → read today's meals → `MealWindowChecker` → start overlay if missed → re-arm next occurrence | Scheduler, MealRepository | — |
| `ReminderBootReceiver` | Re-arm all alarms after device reboot | Scheduler | — |
| `ReminderOverlayService` | Host the floating popup (WindowManager `TYPE_APPLICATION_OVERLAY`): Lottie + 3 buttons | WindowManager | — |
| `ReminderSettingsScreen` + `ReminderSettingsViewModel` | UI to edit times/toggles + grant overlay permission | Store | ✅ ViewModel |

## Data Flow

```
AlarmManager (per enabled slot)
   → ReminderAlarmReceiver.onReceive (goAsync)
        ├─ currentUid() == null?  → skip (still re-arm next day)
        ├─ read today's complete meals (mealRepository.meals.first(), filtered)
        ├─ MealWindowChecker.isWindowMissed(slotIndex, settings, meals)
        │      └─ missed && Settings.canDrawOverlays()
        │             → start ReminderOverlayService(mealLabel, slotIndex)
        └─ ReminderScheduler.armNextOccurrence(slotIndex)

ReminderOverlayService (popup buttons)
   ├─ "רשום ארוחה"        → launch MainActivity deep-link DEST_ADD_MEAL; remove overlay; stop
   ├─ "תזכיר עוד 30 דק'"  → ReminderScheduler.snooze(slotIndex, +30min); remove overlay; stop
   └─ "ביטול"             → remove overlay; stop
```

## Proposed Changes

### Component 1: Settings data & persistence

#### [NEW] `data/reminders/ReminderSettings.kt`
- `data class ReminderSlot(val time: LocalTime, val mealLabel: String, val enabled: Boolean)`.
- `data class ReminderSettings(val masterEnabled: Boolean, val slots: List<ReminderSlot>)`
  with a `DEFAULT` (master on; the three default slots above).

#### [NEW] `data/reminders/ReminderSettingsStore.kt`
- `interface ReminderSettingsStore { val settings: Flow<ReminderSettings>; suspend fun update(new: ReminderSettings) }`.
- `InMemoryReminderSettingsStore` (test/fallback) and `DataStoreReminderSettingsStore`
  (device-local, never synced — same pattern as `DataStoreCelebrationStore`). Times stored
  as `HH:mm` strings; labels are stable constants (not persisted user text).

#### [MODIFY] `di/AppContainer.kt`
- Add a lazy `reminderSettingsStore` (DataStore-backed, initialized with app context in
  `init`, mirroring `initCelebrations`). Add a lazy `reminderScheduler`.

---

### Component 2: Core logic (pure)

#### [NEW] `data/reminders/MealWindowChecker.kt`
- `fun isWindowMissed(slotIndex: Int, settings: ReminderSettings, todaysMealTimes: List<LocalTime>): Boolean`.
- Computes `[prevSlotTime, thisSlotTime)` (prev = midnight for slot 0, using only
  **enabled** slots to define boundaries) and returns `true` when no meal time falls in it.
- Boundary rule: start inclusive, end exclusive. No Android imports.

---

### Component 3: Scheduling

#### [NEW] `sync/ReminderScheduler.kt` (or `notification/`)
- `object`/class wrapping `AlarmManager`, styled after `HealthSyncScheduler`.
- `armAll(context)`: for each enabled slot, schedule the next occurrence.
- `armNextOccurrence(context, slotIndex)`: compute the next `slotTime` (today if still
  ahead, else tomorrow) and `setAndAllowWhileIdle` a `PendingIntent` → `ReminderAlarmReceiver`
  with `requestCode = slotIndex` and an extra carrying the slot index.
- `snooze(context, slotIndex, minutes = 30)`: one-shot alarm `now + minutes` for the same
  slot (re-runs the same window check at snooze time).
- `cancelAll(context)`: cancel every slot PendingIntent (called on sign-out and when the
  master toggle is turned off).

---

### Component 4: Receivers

#### [NEW] `notification/ReminderAlarmReceiver.kt`
- `BroadcastReceiver`. On the slot alarm:
  - If `AppContainer.currentUid() == null` → re-arm next occurrence and return.
  - `goAsync()` + coroutine: read today's `complete` meals
    (`mealRepository.meals.first()` filtered by `date` and `status`), map `loggedAt` →
    `LocalTime` (device zone), run `MealWindowChecker.isWindowMissed(...)`.
  - If missed **and** `Settings.canDrawOverlays(context)` → start `ReminderOverlayService`
    with the slot's `mealLabel` and index.
  - Always re-arm the next occurrence of this slot.

#### [NEW] `notification/ReminderBootReceiver.kt`
- Handles `ACTION_BOOT_COMPLETED`; calls `ReminderScheduler.armAll` (reads settings first;
  no-op if master disabled).

---

### Component 5: Overlay popup

#### [NEW] `notification/ReminderOverlayService.kt`
- Started `Service`. Guards on `Settings.canDrawOverlays`; self-stops if not granted.
- Adds a `ComposeView` to `WindowManager` with `TYPE_APPLICATION_OVERLAY`,
  `FLAG_NOT_FOCUSABLE`-style flags so the popup floats without stealing global focus.
  The `ComposeView` is given a lightweight `LifecycleOwner` + `SavedStateRegistryOwner` +
  `ViewModelStoreOwner` so Compose renders inside the window (standard overlay-Compose
  requirement).
- Because the app holds `SYSTEM_ALERT_WINDOW`, starting this service and adding the window
  from the background is permitted on Android 12+. If a foreground-service start is
  required on a given OS level, it starts briefly as a foreground service with a minimal
  transient notification; otherwise a plain started service.
- Content: a rounded card with the Lottie waiter (`lottie-compose`, intro-then-loop),
  the title (e.g. "לא רשמת ארוחת בוקר 🍳"), and three buttons wired to the actions in the
  Data Flow above. Removes its window view in `onDestroy`.

#### [NEW] Lottie asset `res/raw/waiter_reminder.json`
- Supplied by the design agent (transparent background, vector). Placeholder until then.

---

### Component 6: UI & navigation

#### [NEW] `ui/reminders/ReminderSettingsScreen.kt` + `ReminderSettingsViewModel.kt`
- Master enable `Switch`.
- One row per slot: meal label, a time picker (`TimePickerDialog`/M3 time picker), and an
  enable `Switch`.
- An "overlay permission" status row: shows granted/not-granted and a button that launches
  `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` when missing.
- ViewModel reads/writes `ReminderSettingsStore`; on any change it persists and calls
  `ReminderScheduler.armAll` / `cancelAll` so the schedule always matches the settings.

#### [MODIFY] `ui/profile/ProfileScreen.kt`
- Add a row (e.g. "תזכורות ארוחה") navigating to the reminder settings screen, alongside
  the existing quick-actions toggle.

#### [MODIFY] `Navigation.kt` / `NavigationKeys.kt`
- Add a `ReminderSettings` route and an `onNavigateToReminderSettings` hook threaded
  through `MainScreen` → `ProfileScreen`.

---

### Component 7: Manifest & wiring

#### [MODIFY] `AndroidManifest.xml`
- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`.
- `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`.
- `<receiver android:name=".notification.ReminderAlarmReceiver" android:exported="false" />`.
- `<receiver android:name=".notification.ReminderBootReceiver" android:exported="false">`
  with an `ACTION_BOOT_COMPLETED` intent-filter.
- `<service android:name=".notification.ReminderOverlayService" android:exported="false" />`
  (with `foregroundServiceType` if the FGS path is used).

#### [MODIFY] app startup (`MyHealthApp` / `MainActivity`) & sign-out (`AuthViewModel`)
- On authenticated startup with `masterEnabled`, call `ReminderScheduler.armAll`.
- On sign-out, call `ReminderScheduler.cancelAll` (mirrors the existing periodic-sync
  cancellation on sign-out).

#### [MODIFY] `app/build.gradle`
- Add `com.airbnb.android:lottie-compose` dependency.

## Iron-Rule Compliance

- **#4 minimal permissions**: inexact alarms (no exact-alarm permission); only
  `SYSTEM_ALERT_WINDOW` + `RECEIVE_BOOT_COMPLETED` added; no notification permission
  (this is not a drawer notification).
- **#5 no medical advice**: the popup is a neutral logging nudge, no health claims.
- **#6 no blocking on main thread**: DataStore, meal reads, and the receiver use
  coroutines / `goAsync`.
- **#2 food images stay local**: unaffected — this feature reads only meal metadata.
- **Phase isolation**: builds on Phase-2 meal data (read-only); does not touch the daily
  summary / insights pipeline.

## Verification Plan

### Automated Tests
- `MealWindowChecker`: table of cases — empty window; a meal exactly on the start boundary
  (counts) and on the end boundary (excluded); a `failed`/`analyzing` meal ignored; meals
  that fall only in other windows; a disabled middle slot widening a later window.
- `ReminderSettingsStore` (InMemory + DataStore round-trip parse/serialize of times).
- `ReminderSettingsViewModel`: toggling master/slots and editing a time persists and
  triggers `armAll`/`cancelAll`; overlay-permission status is reflected.
- Run `./gradlew test`.

### Manual Verification
1. **Grant flow**: open reminder settings, tap grant, approve overlay permission, return —
   status shows granted.
2. **Missed window**: with no meal logged, set a slot time to a minute ahead, lock the app
   / open another app; at the time, the floating waiter popup appears over that app.
3. **Not missed**: log a meal in the window; at the slot time no popup appears.
4. **Buttons**: "רשום ארוחה" opens the app on the Add-Meal sheet; "תזכיר עוד 30 דק'"
   dismisses and re-fires ~30 min later; "ביטול" dismisses with no re-fire.
5. **Reboot**: reboot the device; confirm reminders still fire (boot re-arm).
6. **Sign-out**: sign out; confirm no popups fire.
