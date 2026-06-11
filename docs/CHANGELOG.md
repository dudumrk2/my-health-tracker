# Changelog

> **For agents:** Read this before starting work. Add an entry when you finish a phase or make a significant architectural decision. Keep entries SHORT — one line for the change, one line for the why. No essays. Date format: `yyyy-MM-dd`.

---

## Current State
Phase 1 complete (merged to `feat/foundation-and-health`, PR open). Phase 2 not started.

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
