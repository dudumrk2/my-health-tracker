# Meal Reminders (Floating Overlay) Design

This document details the design for **meal-logging reminders** that surface as a
floating popup **over other apps** (not a drawer notification) at configurable **meal
times**. The purpose is to **prompt the user to photograph / log the meal in the moment**
— before they finish eating, when there is still food to capture. At each meal time the
popup appears **unless that meal has already been logged**. The popup shows a playful
waiter character (a static `drawable` animated natively in Compose) that slides up from
the bottom, with three actions: **log a meal**, **snooze 30 minutes**, or **dismiss**.

The core problem being solved: the user forgets to log meals, and forgetting means the
app is usually *not open* — so an in-app-only reminder can't help. The reminder must
reach the user while the app is closed, at the moment they are about to eat.

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
> - **Default times**: `07:00` / `12:00` / `19:00` (breakfast / lunch / dinner). These are
>   meal *times* — the reminder fires at the meal so the user can photograph it — not
>   deadlines after the fact. All three times are editable in the new settings screen.
> - **Waiter asset & animation**: The animation is **native Compose**, not Lottie. A static
>   `app/src/main/res/drawable/waiter.png` is animated via `AnimatedVisibility`
>   (`slideInVertically` + spring overshoot) plus an `InfiniteTransition` idle loop
>   (hover + breathing). The composable already exists as
>   `ui/meal/MealReminderOverlay.kt`. No `lottie-compose` dependency and no `res/raw`
>   JSON are needed.

## Reminder Timing & Suppression

Reminders are an ordered list of "slots", each `{ time, mealLabel, enabled }`. Each slot
is a **meal-time prompt**: at slot `i`'s time `Tᵢ` the popup fires **unless the user has
already logged that meal**.

"Already logged that meal" means a `complete` meal whose `loggedAt` falls in the
**suppression window** `[Sᵢ, now]`, where:
- `Sᵢ` = the **midpoint** between the previous enabled slot's time and `Tᵢ`
  (`S₀` = start of day, `00:00`, for the first slot).
- `now` = the moment the check runs (equal to `Tᵢ`, or the snooze time on a re-check).

The midpoint boundary is what keeps each meal independent: logging breakfast does **not**
suppress the lunch prompt, but logging lunch a bit early does suppress it.

Defaults (with `07:00 / 12:00 / 19:00`):
- `07:00` breakfast → suppression window `[00:00, now]` → "📸 זמן לתעד את ארוחת הבוקר 🍳"
- `12:00` lunch → suppression window `[09:30, now]` → "📸 זמן לתעד את ארוחת הצהריים 🥗"
- `19:00` dinner → suppression window `[15:30, now]` → "📸 זמן לתעד את ארוחת הערב 🍽️"

Times compare against the device's local time zone. Only meals with `date == today` and
`status == complete` count; `analyzing`/`failed` meals do not suppress a reminder.

## Architecture

Small, isolated units. The decision logic is pure Kotlin (no Android), so it is fully
unit-testable; the Android pieces (scheduler, receiver, service) stay thin.

| Unit | Responsibility | Depends on | Tested |
|------|----------------|------------|--------|
| `ReminderSettings` + `ReminderSettingsStore` | Persist master toggle + 3 slots (time/label/enabled) in DataStore | DataStore | ✅ InMemory |
| `MealReminderPolicy` (pure) | Given a slot index, settings, today's meal times, and now → should the reminder fire? | — | ✅ unit |
| `ReminderScheduler` | Wrap `AlarmManager`: (re)arm each enabled slot's next occurrence; snooze; cancel all | AlarmManager | — |
| `ReminderAlarmReceiver` | Fires at a slot time → read today's meals → `MealReminderPolicy` → show overlay if due → re-arm next occurrence | Scheduler, MealRepository | — |
| `ReminderBootReceiver` | Re-arm all alarms after device reboot | Scheduler | — |
| `ReminderOverlayService` | Host the floating popup: a `ComposeView` in WindowManager (`TYPE_APPLICATION_OVERLAY`) rendering `MealReminderOverlay` | WindowManager | — |
| `MealReminderOverlay` (composable, **exists**) | Native waiter animation (slide-up + hover/breathing) + card + 3 buttons | drawable `waiter` | — |
| `ReminderSettingsScreen` + `ReminderSettingsViewModel` | UI to edit times/toggles + grant overlay permission | Store | ✅ ViewModel |

## Data Flow

```
AlarmManager (per enabled slot)
   → ReminderAlarmReceiver.onReceive (goAsync)
        ├─ currentUid() == null?  → skip (still re-arm next day)
        ├─ read today's complete meals (mealRepository.meals.first(), filtered)
        ├─ MealReminderPolicy.shouldRemind(slotIndex, settings, mealTimes, now)
        │      └─ due && Settings.canDrawOverlays()
        │             → start ReminderOverlayService(mealLabel, slotIndex)
        └─ ReminderScheduler.armNextOccurrence(slotIndex)

ReminderOverlayService (popup buttons)
   ├─ "רשום ארוחה"        → launch MainActivity deep-link DEST_ADD_MEAL; remove overlay; stop
   ├─ "תזכיר עוד 30 דק'"  → ReminderScheduler.snooze(slotIndex, +30min); remove overlay; stop
   └─ "ביטול"             → remove overlay; stop
```

On a snooze re-check, `MealReminderPolicy.shouldRemind` runs again with the later `now`,
so if the user photographed the meal during the 30 minutes, the snoozed popup is
suppressed automatically.

## Proposed Changes

### Component 1: Settings data & persistence

#### [NEW] `data/reminders/ReminderSettings.kt`
- `data class ReminderSlot(val time: LocalTime, val mealLabel: String, val enabled: Boolean)`.
- `data class ReminderSettings(val masterEnabled: Boolean, val slots: List<ReminderSlot>)`
  with a `DEFAULT` (master on; breakfast/lunch/dinner at 07:00/12:00/19:00).

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

#### [NEW] `data/reminders/MealReminderPolicy.kt`
- `fun shouldRemind(slotIndex: Int, settings: ReminderSettings, todaysMealTimes: List<LocalTime>, now: LocalTime): Boolean`.
- Computes the suppression window `[Sᵢ, now]` where `Sᵢ` = midpoint between the previous
  **enabled** slot's time and this slot's time (`S₀` = `00:00`). Returns `false` when any
  meal time falls in that window (meal already logged), `true` otherwise.
- No Android imports.

---

### Component 3: Scheduling

#### [NEW] `sync/ReminderScheduler.kt` (or `notification/`)
- `object`/class wrapping `AlarmManager`, styled after `HealthSyncScheduler`.
- `armAll(context)`: for each enabled slot, schedule the next occurrence.
- `armNextOccurrence(context, slotIndex)`: compute the next `slotTime` (today if still
  ahead, else tomorrow) and `setAndAllowWhileIdle` a `PendingIntent` → `ReminderAlarmReceiver`
  with `requestCode = slotIndex` and an extra carrying the slot index.
- `snooze(context, slotIndex, minutes = 30)`: one-shot alarm `now + minutes` for the same
  slot (re-runs the same suppression check at snooze time).
- `cancelAll(context)`: cancel every slot PendingIntent (called on sign-out and when the
  master toggle is turned off).

---

### Component 4: Receivers

#### [NEW] `notification/ReminderAlarmReceiver.kt`
- `BroadcastReceiver`. On the slot alarm:
  - If `AppContainer.currentUid() == null` → re-arm next occurrence and return.
  - `goAsync()` + coroutine: read today's `complete` meals
    (`mealRepository.meals.first()` filtered by `date` and `status`), map `loggedAt` →
    `LocalTime` (device zone), run `MealReminderPolicy.shouldRemind(...)`.
  - If due **and** `Settings.canDrawOverlays(context)` → start `ReminderOverlayService`
    with the slot's `mealLabel` and index.
  - Always re-arm the next occurrence of this slot.

#### [NEW] `notification/ReminderBootReceiver.kt`
- Handles `ACTION_BOOT_COMPLETED`; calls `ReminderScheduler.armAll` (reads settings first;
  no-op if master disabled).

---

### Component 5: Overlay popup

#### [NEW] `notification/ReminderOverlayService.kt`
- Started `Service`. Guards on `Settings.canDrawOverlays`; self-stops if not granted.
- Adds a `ComposeView` to `WindowManager` as a `MATCH_PARENT`, focusable-but-not-keyboard
  window (`TYPE_APPLICATION_OVERLAY`). The `ComposeView` is given a lightweight
  `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` so Compose renders
  inside the window (standard overlay-Compose requirement).
- Hosts `MealReminderOverlay(isVisible, onLogMeal, onRemindLater, onDismiss)`. The service
  drives an `isVisible` `mutableStateOf`: starts `false`, flips to `true` **after** the
  view attaches so the slide-in plays. On any action it sets `isVisible = false`, waits
  ~300 ms for the exit animation, then removes the window view and `stopSelf`.
- Because the app holds `SYSTEM_ALERT_WINDOW`, starting this service and adding the window
  from the background is permitted on Android 12+. If a foreground-service start is
  required on a given OS level, it starts briefly as a foreground service with a minimal
  transient notification; otherwise a plain started service.
- The overlay's full-screen scrim dims the whole screen behind it (a deliberate modal
  feel) and consumes touches; the buttons dispatch the actions in the Data Flow above.

#### [MODIFY] `ui/meal/MealReminderOverlay.kt` (already exists)
- Native composable: `AnimatedVisibility` slide-up (spring overshoot) + `InfiniteTransition`
  hover/breathing over `R.drawable.waiter`, a Material 3 card, and the three buttons.
- Small enhancement: accept a `title: String` (and optional body) param so the service can
  pass the per-meal text (e.g. "📸 זמן לתעד את ארוחת הבוקר 🍳"), instead of the current
  hard-coded copy.

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

#### `app/build.gradle`
- No new dependency needed — the animation is native Compose over a drawable.

## Iron-Rule Compliance

- **#4 minimal permissions**: inexact alarms (no exact-alarm permission); only
  `SYSTEM_ALERT_WINDOW` + `RECEIVE_BOOT_COMPLETED` added; no notification permission
  (this is not a drawer notification).
- **#5 no medical advice**: the popup is a neutral logging nudge, no health claims.
- **#6 no blocking on main thread**: DataStore, meal reads, and the receiver use
  coroutines / `goAsync`.
- **#2 food images stay local**: unaffected — this feature reads only meal metadata; the
  photo the user takes still stays on-device per the existing rule.
- **Phase isolation**: builds on Phase-2 meal data (read-only); does not touch the daily
  summary / insights pipeline.

## Verification Plan

### Automated Tests
- `MealReminderPolicy.shouldRemind`: table of cases — no meal logged (fires); breakfast
  logged at 08:00 does not suppress the 12:00 lunch prompt (fires); lunch logged at 11:30
  suppresses the 12:00 prompt (skips); a meal exactly on the midpoint boundary (skips) vs
  just before it (fires); a `failed`/`analyzing` meal does not suppress; a disabled middle
  slot shifts the next slot's midpoint boundary.
- `ReminderSettingsStore` (InMemory + DataStore round-trip parse/serialize of times).
- `ReminderSettingsViewModel`: toggling master/slots and editing a time persists and
  triggers `armAll`/`cancelAll`; overlay-permission status is reflected.
- Run `./gradlew test`.

### Manual Verification
1. **Grant flow**: open reminder settings, tap grant, approve overlay permission, return —
   status shows granted.
2. **Meal-time prompt**: with no meal logged, set a slot time to a minute ahead, open
   another app; at the time, the floating waiter popup appears over that app.
3. **Already logged**: log a meal just before the slot time; at the slot time no popup
   appears.
4. **Buttons**: "רשום ארוחה" opens the app on the Add-Meal sheet; "תזכיר עוד 30 דק'"
   dismisses and re-fires ~30 min later (and self-suppresses if you logged meanwhile);
   "ביטול" dismisses with no re-fire.
5. **Reboot**: reboot the device; confirm reminders still fire (boot re-arm).
6. **Sign-out**: sign out; confirm no popups fire.
