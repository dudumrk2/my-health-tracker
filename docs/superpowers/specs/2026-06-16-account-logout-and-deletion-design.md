# Design — Logout & Account Deletion (Profile screen + inactivity cleanup)

> Status: approved design, pending implementation plan.
> Date: 2026-06-16. Branch: `feat/account-deletion`.

## Goal

Add to the Profile screen the ability to **log out** and to **permanently delete the
user's account and all personal data**, with an explicit warning before deletion.
Because Android provides no uninstall hook, also approximate "delete data after the
app is removed" with a **monthly inactivity-cleanup cron** that purges users who have
not opened the app and not logged meals for a month.

## Scope decisions (locked)

- **Deletion meaning:** full deletion — all Firestore data under `users/{uid}` **and**
  the Firebase Auth account (irreversible).
- **Placement:** Logout + Delete buttons at the **bottom of the Profile screen**. The
  existing dashboard-overflow logout stays unchanged.
- **Deletion mechanism:** server-side via a **callable Cloud Function** (Admin SDK),
  not client-side iteration. The Admin SDK deletes the auth user without requiring a
  fresh client re-login.
- **Uninstall approximation:** a **monthly scheduled function** deletes users inactive
  for `INACTIVE_DAYS = 30`. Activity signal = **app open + meal logging only**
  (background Health-Connect sync is intentionally *not* counted).
- **Confirmation:** a simple two-button Material3 `AlertDialog` with a red destructive
  confirm (not type-to-confirm). Single-user personal app — proportionate.

## Why uninstall can't trigger deletion directly

Android delivers no callback/broadcast to an app being uninstalled; an app cannot run
code on its own removal. The OS does wipe the app's **local** data (including the local
Firestore offline cache) on uninstall, so only the **cloud** copy + auth account
persist. Those can only be removed server-side after the fact — hence the cron.

---

## Architecture

### 1. Shared server-side purge helper — `purgeUser(uid)`

A single function (e.g. `functions/src/account/purgeUser.ts`) used by **both** the
manual callable and the cron, so both paths delete identically:

```
purgeUser(uid):
  await getFirestore().recursiveDelete(getFirestore().doc(`users/${uid}`))  // doc + ALL subcollections
  await getAuth().deleteUser(uid)
```

`recursiveDelete` removes the user doc (including the `profile` field) and every
subcollection: `healthDaily`, `meals`, `water`, `bodyMeasurements`, `insights`.
Firestore is deleted **before** the auth account so a Firestore failure leaves the
account intact and retryable. Returns normally on success; throws on failure.

### 2. Callable Cloud Function — `deleteUserData`

New `functions/src/deleteUserData.ts`, exported from `index.ts`. Mirrors the
`analyzeMeal` shape:

- `onCall({ enforceAppCheck: true, region: FUNCTION_REGION })`.
- Rejects unauthenticated calls (`HttpsError("unauthenticated", …)`).
- Calls `purgeUser(request.auth.uid)`.
- Returns `{ success: true }`. Logs `requestId/uid/durationMs` like existing functions.
- On failure maps to `HttpsError("internal", …)`.

> ⚠️ **Manual deploy (user action):** `firebase deploy --only functions:deleteUserData`.

### 3. Scheduled Cloud Function — `cleanupInactiveUsers`

New `functions/src/cleanupInactiveUsers.ts`, exported from `index.ts`. Mirrors the
`generateInsights` scheduled pattern (`onSchedule`, TZ, per-user failures logged & skipped).

- Schedule: monthly, env-configurable. Default `CLEANUP_SCHEDULE = "0 3 1 * *"`,
  `TZ = Asia/Jerusalem`.
- `INACTIVE_DAYS = 30` (env-configurable).
- Iterates the `users` collection. For each user:

```
lastActive   = users/{uid}.lastActiveAt ?? users/{uid}.profile.createdAt   // app-open signal
lastMeal     = max loggedAt across users/{uid}/meals   (query orderBy loggedAt desc limit 1)
signal       = max(lastActive, lastMeal)
if signal < now - INACTIVE_DAYS  →  purgeUser(uid)
```

- A per-user failure is logged and skipped (never fatal for the rest). Each deletion is
  logged (`uid`, `signal`, `now`).
- **Legacy/edge safety:** when `lastActiveAt` is missing, fall back to `createdAt` so a
  brand-new account whose first `lastActiveAt` write hasn't landed isn't deleted; "field
  missing" alone never counts as inactive.
- **Health sync excluded by decision:** `healthDaily.syncedAt` is *not* part of the
  signal. A still-installed device that only syncs health data in the background (never
  opened, no meals for a month) will be purged. This is the chosen behavior.

> ⚠️ **Manual deploy (user action):** included in `firebase deploy --only functions`.
> v2 `onSchedule` auto-creates the Cloud Scheduler job — no separate console step.

### 4. Client — `lastActiveAt` heartbeat

The cron needs an "app opened" timestamp. Add a write of `lastActiveAt`
(`FieldValue.serverTimestamp()`, merged into `users/{uid}`) on **app foreground**:

- A `ProcessLifecycleOwner` `ON_START` observer (androidx `lifecycle-process`) registered
  in the `Application`/`MainActivity`, firing once per foreground (not per screen).
- No-op when signed out (`currentUid() == null`).
- Implemented via a small data-layer method, e.g. `ActivityRepository.touchLastActive(uid)`
  (Firestore `set(merge)` on `users/{uid}` with `lastActiveAt`). One cheap write per open.

> Stored as a **top-level** `lastActiveAt` field on `users/{uid}` (sibling of `profile`),
> so it never collides with the `profile` map written by `saveUserProfile`.

### 5. Client — data layer for deletion

- New `AccountRepository` interface + `FunctionsAccountRepository` calling the
  `deleteUserData` callable, mirroring `FunctionsMealAnalyzer` (including Hebrew error
  mapping for `FirebaseFunctionsException`: `UNAUTHENTICATED` → "נדרשת התחברות מחדש.",
  otherwise a generic Hebrew failure message).
- Registered in `AppContainer` as `accountRepository` (and `activityRepository` for §4).

### 6. Client — ViewModel

Extend `ProfileViewModel`:

- New `accountState: StateFlow<AccountState>` with `Idle / Deleting / Deleted / Error(msg)`.
- `deleteAccount()` → sets `Deleting`, calls `accountRepository.deleteAccount()`, then
  `Deleted` on success or `Error` on failure.
- The ViewModel performs **only** the remote delete. Local session cleanup (cancel
  WorkManager + local `signOut`) stays in Navigation, reusing `authViewModel.signOut(context)`
  — the same separation logout already uses.

### 7. Client — UI (Profile screen)

New "חשבון" card at the bottom of `ProfileScreenContent` (below the save button):

- **התנתקות** — neutral button → `onLogout`.
- **מחיקת חשבון ונתונים** — error-colored button → opens the warning dialog.

**Warning dialog** (the required "התראה מוקדמת") — Material3 `AlertDialog`:
- Title: "מחיקת חשבון ונתונים".
- Body: permanently deletes all data (ארוחות, נתוני בריאות, תובנות, פרופיל) **and** the
  account; cannot be undone.
- Confirm (red): "מחק לצמיתות" → `onDeleteAccount`. Dismiss: "ביטול".
- While `accountState == Deleting`: progress indicator, buttons disabled.
- On `Error`: show the Hebrew message.
- On `Deleted`: invoke `onAccountDeleted`.

### 8. Wiring — `Navigation.kt`

The `Profile` entry gains (with `LocalContext` available in the entry):

- `onLogout = { authViewModel.signOut(context); backStack.clear(); backStack.add(Auth) }`
- `onAccountDeleted = { authViewModel.signOut(context); backStack.clear(); backStack.add(Auth) }`

Existing `onSaveSuccess` (save / cancel-back) is unchanged.

### 9. Security & Firestore Rules

No rules change. All deletion runs through the Admin SDK server-side; the client never
deletes Firestore directly. The `lastActiveAt` write is covered by the existing
"user may write their own `users/{uid}`" rule (verify the rule isn't restricted to the
`profile` field only; widen to allow the `lastActiveAt` field if needed).

---

## Testing

**Functions (TS):**
- `purgeUser`: calls `recursiveDelete` then `deleteUser` with the right uid, in order
  (Firestore before auth); surfaces failure.
- `deleteUserData`: unauthenticated → `HttpsError("unauthenticated")`; authenticated →
  `purgeUser(uid)` invoked; success shape `{ success: true }`.
- `cleanupInactiveUsers` selection logic (pure, dependency-injected): given per-user
  `lastActiveAt` / `createdAt` / latest-meal `loggedAt`, computes `signal` and decides
  purge vs keep at the 30-day boundary; missing `lastActiveAt` falls back to `createdAt`;
  one user's failure doesn't abort the rest.

**Client (Kotlin):**
- `ProfileViewModel.deleteAccount`: state transitions `Idle → Deleting → Deleted` (success)
  and `→ Error` (failure) via a fake `AccountRepository` (mirrors `FakeRepository`).
- `FunctionsAccountRepository` exception → Hebrew message mapping.
- `ActivityRepository.touchLastActive`: no-op when signed out; writes `lastActiveAt` merge
  when signed in (against a fake/emulator).

**Run:** `./gradlew test` (client) and the functions test suite before PR.

---

## Manual user actions (stop and request)

1. Deploy functions: `firebase deploy --only functions` (publishes `deleteUserData` +
   `cleanupInactiveUsers`; the latter auto-creates its Cloud Scheduler job).

## Out of scope

- Type-to-confirm deletion, data export before delete, soft-delete/grace-period restore,
  email warnings before cron deletion, multi-device "logout everywhere".
