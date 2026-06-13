# Changelog

> **For agents:** Read this before starting work. Add an entry when you finish a phase or make a significant architectural decision. Keep entries SHORT — one line for the change, one line for the why. No essays. Date format: `yyyy-MM-dd`.

---

## Current State
Phase 1 complete. Phase 1.5 patch complete (gender validation + manual workout logging). Phase 2 in progress (meal logging + AI analysis).

---

## Phase 1.5 — Gender Validation + Manual Workout Logging · 2026-06-13

### Implemented
- Gender required field validation in `ProfileRepository.validateProfile()` — returns `Result.failure("Gender is required")`.
- `source` field on `ExerciseSessionInfo`: `"health_connect"` | `"manual"`. Document-level `source` becomes `"mixed"` when both exist.
- `HealthRepository.saveManualWorkout()` — appends manual workout to Firestore `healthDaily/{date}` using `SetOptions.merge()`.
- `AddWorkoutViewModel` dual-writes: `FakeRepository` (immediate UI) + Firestore when user is authenticated.
- `ActivityScreen` badge now uses `workout.source == "manual"` instead of a type-string hack.
- `FakeRepository` singleton — in-memory mock used by all ViewModels; `addWorkout()` sets `source = "manual"`.
- Full UI screens implemented in parallel task: Activity, Body, Food/Meal, Main shell with 3-tab nav.
- 25 unit tests passing (`ProfileAndHealthUnitTest` + `UiValidationTests`).

### Key Decisions
- **`FakeRepository` dual-write pattern** — all ViewModels read/write `FakeRepository` for instant UI; Firestore writes happen in the background guarded by try/catch so unit tests work without Firebase initialization.
- **Gender validated in `ProfileRepository`, not just `ProfileViewModel`** — ensures validation is enforced at the data layer regardless of which ViewModel calls save.

---

## Phase 1 — Foundation & Health Connect · 2026-06-11

### Implemented
- Android project: Kotlin + Compose + MVVM
- Firebase Auth — Google Sign-In, persisted auth state
- Firestore profile (`users/{uid}/profile`)
- Health Connect: Steps, SleepSession, ExerciseSession
- WorkManager periodic sync (6h) + manual sync from Dashboard
- Auth / Profile / Dashboard screens

### Key Decisions
- **Navigation3 (alpha 1.0.1)** — chosen over Nav 2.x for type-safe `NavKey` back-stack; API unstable but acceptable for a personal project.
- **`SetOptions.merge()` for all Firestore writes** — documents keyed by date are idempotent; safe to re-run sync without duplicates.
- **Sleep aggregation on client before saving** — `sleepMinutes` is pre-computed and stored, so Phase 3 summary reads one field instead of re-aggregating raw sessions.
- **`callbackFlow` wrapping Firebase Tasks** — converts callback API to Flow without adding `kotlinx-coroutines-play-services` as a dependency.
- **DTOs separate from domain models** — `DailyHealthDataDto` / `SleepSessionInfoDto` / `ExerciseSessionInfoDto` isolate Firestore deserialization from business logic.

### PR Review Fixes · 2026-06-11
- Logout back-stack: added `backStack.clear()` before pushing `Auth` — pressing Back after sign-out no longer returns to Dashboard.
- Auth navigation moved into `LaunchedEffect(currentUser)` — prevents `onAuthSuccess()` firing on every recomposition.
- `DashboardViewModel`: replaced nested `collect`-inside-`collect` with `combine()` — the old pattern leaked a new inner coroutine on every profile update.
- `AuthViewModel` promoted to `AndroidViewModel`; `signOut()` cancels `"HealthConnectSyncWork"` — WorkManager no longer polls after logout.
- Instrumented tests rewritten against Firebase Local Emulator (`10.0.2.2:8080`) — previously used mocks, violating the blueprint requirement.
- Added missing unit tests: `mapHealthConnectData` (empty / sleep / exercise), `mapExerciseType` (Walking / Running / unknown), `HealthConnectManager` SDK-unavailable mock tests.
- Removed dead code: `DataRepository`, `MainScreen`, `MainScreenViewModel`.
- Config: `isMinifyEnabled = true` in release; `fullBackupContent` linked in manifest; step goal extracted to `DAILY_STEP_GOAL` constant.
