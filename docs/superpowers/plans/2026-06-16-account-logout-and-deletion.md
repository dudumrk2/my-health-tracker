# Logout & Account Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user log out and permanently delete their account + all personal data from the Profile screen (with a warning), and approximate "delete data after uninstall" with a monthly inactivity-cleanup cron.

**Architecture:** A shared server-side `purgeUser(uid)` helper (Firestore `recursiveDelete` then `getAuth().deleteUser`) is called by both a new callable `deleteUserData` (App Check, manual button) and a new scheduled `cleanupInactiveUsers` (monthly). The client writes a `lastActiveAt` heartbeat on app foreground; the cron deletes users whose `max(lastActiveAt ?? createdAt, latest meal loggedAt)` is older than 30 days. The Profile screen gains an "חשבון" card with logout + a red destructive delete that opens a confirm dialog.

**Tech Stack:** Cloud Functions v2 (TypeScript, firebase-admin, Jest), Android (Kotlin, Jetpack Compose, MVVM, Firebase Functions/Firestore SDK, JUnit4 + MockK + coroutines-test).

**Reference design:** `docs/superpowers/specs/2026-06-16-account-logout-and-deletion-design.md`

---

## File Structure

**Backend (functions):**
- Create `functions/src/account/purgeUser.ts` — `PurgeDeps`, `purgeUser`, `prodPurgeDeps`.
- Create `functions/src/deleteUserData.ts` — callable + testable `handleDeleteUserData`.
- Create `functions/src/cleanupInactiveUsers.ts` — pure selection logic + scheduled function.
- Modify `functions/src/index.ts` — export the two new functions.
- Create `functions/test/purgeUser.test.ts`, `functions/test/deleteUserData.test.ts`, `functions/test/cleanupInactiveUsers.test.ts`.

**Client (Android):**
- Create `app/src/main/java/com/myhealthtracker/app/data/account/AccountRepository.kt` — interface, `FunctionsAccountRepository`, `mapDeleteAccountError`.
- Create `app/src/main/java/com/myhealthtracker/app/data/activity/ActivityRepository.kt` — interface + `FirestoreActivityRepository`.
- Modify `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt` — register both.
- Modify `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt` — `AccountState` + `deleteAccount()`.
- Modify `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt` — account card + confirm dialog + new callbacks.
- Modify `app/src/main/java/com/myhealthtracker/app/Navigation.kt` — wire `onLogout` / `onAccountDeleted`.
- Modify `app/src/main/java/com/myhealthtracker/app/MainActivity.kt` — `onStart` heartbeat.
- Create `app/src/test/java/com/myhealthtracker/app/data/account/AccountErrorMappingTest.kt`.
- Create `app/src/test/java/com/myhealthtracker/app/ui/profile/ProfileViewModelDeleteTest.kt`.

**Notes / deliberate decisions:**
- No Firestore Rules change: deletion runs via Admin SDK (bypasses rules); the `lastActiveAt` write is already covered by `match /users/{uid} { allow read, write: if isOwner(uid) }` in `firestore.rules`.
- Heartbeat uses `MainActivity.onStart()` (single-activity app) rather than adding the `lifecycle-process` dependency for `ProcessLifecycleOwner` — functionally equivalent for this app.
- The `onCall` wrappers themselves aren't unit-tested directly (matching the existing repo convention, e.g. `analyzeMeal`); instead the testable core (`handleDeleteUserData`, `purgeUser`, the cleanup selection logic) is covered.

---

## Task 1: Shared `purgeUser` helper (backend)

**Files:**
- Create: `functions/src/account/purgeUser.ts`
- Test: `functions/test/purgeUser.test.ts`

- [ ] **Step 1: Write the failing test**

Create `functions/test/purgeUser.test.ts`:

```typescript
import { purgeUser, PurgeDeps } from "../src/account/purgeUser";

describe("purgeUser", () => {
  function makeDeps() {
    const calls: string[] = [];
    const deps: PurgeDeps = {
      recursiveDelete: async (path: string) => { calls.push(`recursiveDelete:${path}`); },
      deleteAuthUser: async (uid: string) => { calls.push(`deleteAuthUser:${uid}`); },
    };
    return { deps, calls };
  }

  it("deletes Firestore data before the auth account", async () => {
    const { deps, calls } = makeDeps();
    await purgeUser("user123", deps);
    expect(calls).toEqual(["recursiveDelete:users/user123", "deleteAuthUser:user123"]);
  });

  it("does not delete the auth account if Firestore deletion fails", async () => {
    const calls: string[] = [];
    const deps: PurgeDeps = {
      recursiveDelete: async () => { throw new Error("boom"); },
      deleteAuthUser: async (uid: string) => { calls.push(`deleteAuthUser:${uid}`); },
    };
    await expect(purgeUser("user123", deps)).rejects.toThrow("boom");
    expect(calls).toEqual([]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest purgeUser`
Expected: FAIL — cannot find module `../src/account/purgeUser`.

- [ ] **Step 3: Write minimal implementation**

Create `functions/src/account/purgeUser.ts`:

```typescript
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";

if (getApps().length === 0) initializeApp();

/** Injectable side effects so the ordering logic is unit-testable without the Admin SDK. */
export interface PurgeDeps {
  recursiveDelete(path: string): Promise<void>;
  deleteAuthUser(uid: string): Promise<void>;
}

/**
 * Permanently deletes a user. Firestore data (the `users/{uid}` doc and ALL its
 * subcollections) is removed first, then the Firebase Auth account — so a Firestore
 * failure leaves the account intact and the operation retryable.
 */
export async function purgeUser(uid: string, deps: PurgeDeps): Promise<void> {
  await deps.recursiveDelete(`users/${uid}`);
  await deps.deleteAuthUser(uid);
}

/** Production side effects backed by the Admin SDK. */
export function prodPurgeDeps(): PurgeDeps {
  return {
    recursiveDelete: async (path: string) => {
      const db = getFirestore();
      await db.recursiveDelete(db.doc(path));
    },
    deleteAuthUser: async (uid: string) => {
      await getAuth().deleteUser(uid);
    },
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest purgeUser`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/account/purgeUser.ts functions/test/purgeUser.test.ts
git commit -m "feat(functions): add shared purgeUser helper"
```

---

## Task 2: `deleteUserData` callable (backend)

**Files:**
- Create: `functions/src/deleteUserData.ts`
- Modify: `functions/src/index.ts`
- Test: `functions/test/deleteUserData.test.ts`

- [ ] **Step 1: Write the failing test**

Create `functions/test/deleteUserData.test.ts`:

```typescript
import { HttpsError } from "firebase-functions/v2/https";
import { handleDeleteUserData } from "../src/deleteUserData";
import { PurgeDeps } from "../src/account/purgeUser";

describe("handleDeleteUserData", () => {
  function noopDeps(calls: string[]): PurgeDeps {
    return {
      recursiveDelete: async (p) => { calls.push(`recursiveDelete:${p}`); },
      deleteAuthUser: async (u) => { calls.push(`deleteAuthUser:${u}`); },
    };
  }

  it("rejects an unauthenticated request", async () => {
    await expect(handleDeleteUserData(undefined, noopDeps([]))).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("purges the authenticated user and returns success", async () => {
    const calls: string[] = [];
    const result = await handleDeleteUserData({ uid: "abc" }, noopDeps(calls));
    expect(result).toEqual({ success: true });
    expect(calls).toEqual(["recursiveDelete:users/abc", "deleteAuthUser:abc"]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest deleteUserData`
Expected: FAIL — cannot find module `../src/deleteUserData`.

- [ ] **Step 3: Write minimal implementation**

Create `functions/src/deleteUserData.ts`:

```typescript
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { purgeUser, prodPurgeDeps, PurgeDeps } from "./account/purgeUser";

const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";

/** Testable core: enforces auth then purges. Throws HttpsError("unauthenticated") with no auth. */
export async function handleDeleteUserData(
  auth: { uid: string } | undefined,
  deps: PurgeDeps
): Promise<{ success: true }> {
  if (!auth) {
    throw new HttpsError("unauthenticated", "נדרשת התחברות.");
  }
  await purgeUser(auth.uid, deps);
  return { success: true };
}

export const deleteUserData = onCall(
  { enforceAppCheck: true, timeoutSeconds: 120, region: FUNCTION_REGION },
  async (request: CallableRequest): Promise<{ success: true }> => {
    const started = Date.now();
    const uid = request.auth?.uid;
    try {
      const result = await handleDeleteUserData(request.auth, prodPurgeDeps());
      logger.info("deleteUserData ok", { uid, durationMs: Date.now() - started });
      return result;
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("deleteUserData failed", {
        uid, durationMs: Date.now() - started, message: (e as Error).message,
      });
      throw new HttpsError("internal", "לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר.");
    }
  }
);
```

- [ ] **Step 4: Export from index**

Modify `functions/src/index.ts` — add after the existing exports:

```typescript
export { deleteUserData } from "./deleteUserData";
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd functions && npx jest deleteUserData`
Expected: PASS (2 tests).

- [ ] **Step 6: Verify the project still compiles**

Run: `cd functions && npm run lint`
Expected: no TypeScript errors.

- [ ] **Step 7: Commit**

```bash
git add functions/src/deleteUserData.ts functions/src/index.ts functions/test/deleteUserData.test.ts
git commit -m "feat(functions): add deleteUserData callable"
```

---

## Task 3: `cleanupInactiveUsers` scheduled function (backend)

**Files:**
- Create: `functions/src/cleanupInactiveUsers.ts`
- Modify: `functions/src/index.ts`
- Test: `functions/test/cleanupInactiveUsers.test.ts`

- [ ] **Step 1: Write the failing test**

Create `functions/test/cleanupInactiveUsers.test.ts`:

```typescript
import { lastActivitySignal, isInactive, UserActivity } from "../src/cleanupInactiveUsers";

const DAY = 24 * 60 * 60 * 1000;

describe("lastActivitySignal", () => {
  it("takes the max of lastActiveAt and lastMealAt", () => {
    const a: UserActivity = { lastActiveAt: 100, createdAt: 50, lastMealAt: 200 };
    expect(lastActivitySignal(a)).toBe(200);
  });

  it("falls back to createdAt when lastActiveAt is missing", () => {
    const a: UserActivity = { createdAt: 500, lastMealAt: 300 };
    expect(lastActivitySignal(a)).toBe(500);
  });

  it("is 0 when nothing is known", () => {
    expect(lastActivitySignal({})).toBe(0);
  });
});

describe("isInactive", () => {
  const now = 100 * DAY;

  it("is inactive when the newest signal is older than the threshold", () => {
    const a: UserActivity = { lastActiveAt: now - 31 * DAY };
    expect(isInactive(a, now, 30)).toBe(true);
  });

  it("is active when within the threshold", () => {
    const a: UserActivity = { lastActiveAt: now - 29 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });

  it("a recent meal keeps an otherwise-stale user active", () => {
    const a: UserActivity = { lastActiveAt: now - 60 * DAY, lastMealAt: now - 5 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });

  it("missing lastActiveAt falls back to a recent createdAt (kept active)", () => {
    const a: UserActivity = { createdAt: now - 2 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest cleanupInactiveUsers`
Expected: FAIL — cannot find module `../src/cleanupInactiveUsers`.

- [ ] **Step 3: Write minimal implementation**

Create `functions/src/cleanupInactiveUsers.ts`:

```typescript
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { purgeUser, prodPurgeDeps } from "./account/purgeUser";

if (getApps().length === 0) initializeApp();

const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";
const TZ = process.env.INSIGHTS_TZ || "Asia/Jerusalem";
// Monthly, 03:00 on the 1st by default.
const CLEANUP_SCHEDULE = process.env.CLEANUP_SCHEDULE || "0 3 1 * *";
const INACTIVE_DAYS = Number(process.env.CLEANUP_INACTIVE_DAYS || "30");

/** Activity signals in epoch milliseconds. Any field may be absent. */
export interface UserActivity {
  lastActiveAt?: number; // app-foreground heartbeat
  createdAt?: number;    // profile creation (fallback when no heartbeat yet)
  lastMealAt?: number;   // newest meal loggedAt
}

/** Newest known activity. `lastActiveAt` falls back to `createdAt`; meals counted separately. */
export function lastActivitySignal(a: UserActivity): number {
  const base = a.lastActiveAt ?? a.createdAt ?? 0;
  return Math.max(base, a.lastMealAt ?? 0);
}

export function isInactive(a: UserActivity, nowMs: number, inactiveDays: number): boolean {
  const cutoff = nowMs - inactiveDays * 24 * 60 * 60 * 1000;
  return lastActivitySignal(a) < cutoff;
}

function toMillis(v: unknown): number | undefined {
  return v instanceof Timestamp ? v.toMillis() : undefined;
}

/** Reads the activity signals for one user from Firestore. */
async function readUserActivity(uid: string): Promise<UserActivity> {
  const db = getFirestore();
  const userSnap = await db.doc(`users/${uid}`).get();
  const profile = userSnap.get("profile") as Record<string, unknown> | undefined;
  const mealSnap = await db
    .collection(`users/${uid}/meals`)
    .orderBy("loggedAt", "desc")
    .limit(1)
    .get();
  const lastMealAt = mealSnap.empty ? undefined : toMillis(mealSnap.docs[0].get("loggedAt"));
  return {
    lastActiveAt: toMillis(userSnap.get("lastActiveAt")),
    createdAt: profile ? toMillis(profile.createdAt) : undefined,
    lastMealAt,
  };
}

/**
 * Iterates the `users` collection and purges anyone inactive for INACTIVE_DAYS.
 * A failure for one user is logged and skipped — never fatal for the rest.
 */
async function runCleanup(nowMs: number): Promise<void> {
  const users = await getFirestore().collection("users").get();
  let deleted = 0, kept = 0, failed = 0;
  for (const userDoc of users.docs) {
    try {
      const activity = await readUserActivity(userDoc.id);
      if (isInactive(activity, nowMs, INACTIVE_DAYS)) {
        await purgeUser(userDoc.id, prodPurgeDeps());
        deleted++;
        logger.info("cleanup purged inactive user", {
          uid: userDoc.id, signal: lastActivitySignal(activity), nowMs,
        });
      } else {
        kept++;
      }
    } catch (e) {
      failed++;
      logger.error("cleanup user iteration error", { uid: userDoc.id, message: (e as Error).message });
    }
  }
  logger.info("cleanup batch complete", { users: users.size, deleted, kept, failed, inactiveDays: INACTIVE_DAYS });
}

export const cleanupInactiveUsers = onSchedule(
  { schedule: CLEANUP_SCHEDULE, timeZone: TZ, region: FUNCTION_REGION, timeoutSeconds: 540 },
  async () => {
    await runCleanup(Date.now());
  }
);
```

- [ ] **Step 4: Export from index**

Modify `functions/src/index.ts` — add:

```typescript
export { cleanupInactiveUsers } from "./cleanupInactiveUsers";
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd functions && npx jest cleanupInactiveUsers`
Expected: PASS (7 tests).

- [ ] **Step 6: Verify compile + full functions suite**

Run: `cd functions && npm run lint && npx jest`
Expected: no TS errors; all tests pass.

- [ ] **Step 7: Commit**

```bash
git add functions/src/cleanupInactiveUsers.ts functions/src/index.ts functions/test/cleanupInactiveUsers.test.ts
git commit -m "feat(functions): add monthly cleanupInactiveUsers cron"
```

---

## Task 4: `ActivityRepository` — lastActiveAt heartbeat (client)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/activity/ActivityRepository.kt`

> No unit test: this is a thin Firestore `set(merge)` wrapper (the repo doesn't unit-test such wrappers, e.g. it relies on emulator/instrumented coverage). The cron's consumption of `lastActiveAt` is covered in Task 3.

- [ ] **Step 1: Create the repository**

Create `app/src/main/java/com/myhealthtracker/app/data/activity/ActivityRepository.kt`:

```kotlin
package com.myhealthtracker.app.data.activity

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Records app-foreground activity so the server-side inactivity cleanup can tell a live install from an abandoned one. */
interface ActivityRepository {
    suspend fun touchLastActive(uid: String)
}

class FirestoreActivityRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ActivityRepository {

    /** Merges a server-timestamped `lastActiveAt` onto `users/{uid}` (sibling of `profile`). */
    override suspend fun touchLastActive(uid: String) {
        firestore.collection("users").document(uid)
            .set(mapOf("lastActiveAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/activity/ActivityRepository.kt
git commit -m "feat: add ActivityRepository for lastActiveAt heartbeat"
```

---

## Task 5: `AccountRepository` + error mapping (client)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/account/AccountRepository.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/account/AccountErrorMappingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/data/account/AccountErrorMappingTest.kt`:

```kotlin
package com.myhealthtracker.app.data.account

import com.google.firebase.functions.FirebaseFunctionsException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountErrorMappingTest {

    private fun exceptionWith(code: FirebaseFunctionsException.Code): FirebaseFunctionsException {
        val e = mockk<FirebaseFunctionsException>()
        every { e.code } returns code
        return e
    }

    @Test
    fun unauthenticatedMapsToReauthMessage() {
        val msg = mapDeleteAccountError(exceptionWith(FirebaseFunctionsException.Code.UNAUTHENTICATED))
        assertEquals("נדרשת התחברות מחדש.", msg)
    }

    @Test
    fun otherCodesMapToGenericMessage() {
        val msg = mapDeleteAccountError(exceptionWith(FirebaseFunctionsException.Code.INTERNAL))
        assertEquals("לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר.", msg)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.account.AccountErrorMappingTest"`
Expected: FAIL — unresolved reference `mapDeleteAccountError`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/myhealthtracker/app/data/account/AccountRepository.kt`:

```kotlin
package com.myhealthtracker.app.data.account

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/** Thrown when account deletion fails; the message is user-facing Hebrew. */
class AccountDeletionException(message: String) : Exception(message)

/** Pure mapping of a callable failure to a user-facing Hebrew message. */
fun mapDeleteAccountError(e: FirebaseFunctionsException): String = when (e.code) {
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "נדרשת התחברות מחדש."
    else -> "לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר."
}

/** Permanently deletes the signed-in user's account and all their data via the `deleteUserData` Cloud Function. */
interface AccountRepository {
    suspend fun deleteAccount()
}

class FunctionsAccountRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : AccountRepository {

    override suspend fun deleteAccount() {
        try {
            functions.getHttpsCallable("deleteUserData").call().await()
        } catch (e: FirebaseFunctionsException) {
            throw AccountDeletionException(mapDeleteAccountError(e))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.data.account.AccountErrorMappingTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/account/AccountRepository.kt app/src/test/java/com/myhealthtracker/app/data/account/AccountErrorMappingTest.kt
git commit -m "feat: add AccountRepository calling deleteUserData"
```

---

## Task 6: Register repositories in `AppContainer` (client)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`

- [ ] **Step 1: Add the imports**

In `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`, add to the import block:

```kotlin
import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.account.FunctionsAccountRepository
import com.myhealthtracker.app.data.activity.ActivityRepository
import com.myhealthtracker.app.data.activity.FirestoreActivityRepository
```

- [ ] **Step 2: Register the singletons**

In the same file, add after the `insightsRefresher` line (before the `currentUid()` function):

```kotlin
    val accountRepository: AccountRepository by lazy { FunctionsAccountRepository() }
    val activityRepository: ActivityRepository by lazy { FirestoreActivityRepository() }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt
git commit -m "feat: register account and activity repositories in AppContainer"
```

---

## Task 7: `ProfileViewModel.deleteAccount()` + `AccountState` (client)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/profile/ProfileViewModelDeleteTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/ui/profile/ProfileViewModelDeleteTest.kt`:

```kotlin
package com.myhealthtracker.app.ui.profile

import com.myhealthtracker.app.data.account.AccountDeletionException
import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class ProfileViewModelDeleteTest {

    private val dispatcher = StandardTestDispatcher()

    // Profile repo that emits no profile, so init's loadProfile() settles to Idle without Firebase.
    private class FakeProfileRepo : ProfileRepository {
        override fun getUserProfile(uid: String): Flow<Result<UserProfile?>> = flowOf(Result.success(null))
        override fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>> = flowOf(Result.success(Unit))
        override fun validateProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
        override fun calculateAge(birthYear: Int, currentYear: Int): Int = 0
    }

    private class FakeAccountRepo(val error: String? = null) : AccountRepository {
        var called = false
        override suspend fun deleteAccount() {
            called = true
            error?.let { throw AccountDeletionException(it) }
        }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(account: AccountRepository) = ProfileViewModel(
        profileRepository = FakeProfileRepo(),
        uidProvider = { "uid-1" },
        accountRepository = account
    )

    @Test
    fun deleteAccountSuccessReachesDeleted() = runTest(dispatcher) {
        val account = FakeAccountRepo()
        val vm = viewModel(account)
        vm.deleteAccount()
        advanceUntilIdle()
        assertTrue(account.called)
        assertEquals(AccountState.Deleted, vm.accountState.value)
    }

    @Test
    fun deleteAccountFailureReachesErrorWithMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountRepo(error = "boom"))
        vm.deleteAccount()
        advanceUntilIdle()
        val state = vm.accountState.value
        assertTrue(state is AccountState.Error)
        assertEquals("boom", (state as AccountState.Error).message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.profile.ProfileViewModelDeleteTest"`
Expected: FAIL — unresolved references `AccountState`, `accountState`, `deleteAccount`, and the `accountRepository` constructor parameter.

- [ ] **Step 3: Add the state type, dependency, and method**

In `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt`:

(a) Add imports near the existing ones:

```kotlin
import com.myhealthtracker.app.data.account.AccountDeletionException
import com.myhealthtracker.app.data.account.AccountRepository
```

(b) Add this sealed class just below the existing `ProfileUiState` sealed class:

```kotlin
sealed class AccountState {
    object Idle : AccountState()
    object Deleting : AccountState()
    object Deleted : AccountState()
    data class Error(val message: String) : AccountState()
}
```

(c) Add the constructor parameter (after `uidProvider`):

```kotlin
class ProfileViewModel(
    private val profileRepository: ProfileRepository = AppContainer.profileRepository,
    private val uidProvider: () -> String? = { AppContainer.currentUid() },
    private val accountRepository: AccountRepository = AppContainer.accountRepository
) : ViewModel() {
```

(d) Add the state flow next to the existing `_uiState` declarations:

```kotlin
    private val _accountState = MutableStateFlow<AccountState>(AccountState.Idle)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()
```

(e) Add the method (e.g. just before the closing brace of the class):

```kotlin
    /** Permanently deletes the account + all data via the Cloud Function. Local sign-out is handled by the caller. */
    fun deleteAccount() {
        viewModelScope.launch {
            _accountState.value = AccountState.Deleting
            try {
                accountRepository.deleteAccount()
                _accountState.value = AccountState.Deleted
            } catch (e: AccountDeletionException) {
                _accountState.value = AccountState.Error(e.message ?: "שגיאה במחיקת החשבון")
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.profile.ProfileViewModelDeleteTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/profile/ProfileViewModelDeleteTest.kt
git commit -m "feat: add deleteAccount + AccountState to ProfileViewModel"
```

---

## Task 8: Heartbeat on app foreground (client)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`

> No new test: `onStart` is an Android lifecycle hook; the write logic lives in `ActivityRepository` (Task 4) and the consumption is covered in Task 3.

- [ ] **Step 1: Add imports**

In `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`, add to the import block:

```kotlin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Override `onStart` to record activity**

In `MainActivity`, add this method after `onCreate` (and above `onNewIntent`):

```kotlin
    override fun onStart() {
        super.onStart()
        // Record an app-foreground heartbeat so the server-side inactivity cleanup can
        // distinguish a live install from an abandoned one. No-op when signed out.
        val uid = AppContainer.currentUid() ?: return
        lifecycleScope.launch {
            runCatching { AppContainer.activityRepository.touchLastActive(uid) }
        }
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/MainActivity.kt
git commit -m "feat: write lastActiveAt heartbeat on app foreground"
```

---

## Task 9: Account card + confirm dialog on the Profile screen (client)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`

> UI composition; verified by build + preview. The delete state machine is unit-tested in Task 7.

- [ ] **Step 1: Collect `accountState` and add callbacks to `ProfileScreen`**

In `ProfileScreen` (the public composable), after `val calculatedAge by viewModel.calculatedAge.collectAsState()` add:

```kotlin
    val accountState by viewModel.accountState.collectAsState()
```

Change the `ProfileScreen` signature to accept the two new callbacks:

```kotlin
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSaveSuccess: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
```

In the existing `LaunchedEffect(uiState)` block, leave it as-is, and add a new effect right after it to route on successful deletion:

```kotlin
    LaunchedEffect(accountState) {
        if (accountState is AccountState.Deleted) {
            onAccountDeleted()
        }
    }
```

- [ ] **Step 2: Pass the new params into `ProfileScreenContent`**

In the `ProfileScreenContent(...)` call inside `ProfileScreen`, add these arguments (alongside the existing ones, before `modifier = modifier`):

```kotlin
        accountState = accountState,
        onLogoutClick = onLogout,
        onDeleteAccountConfirm = { viewModel.deleteAccount() },
```

- [ ] **Step 3: Add the parameters to `ProfileScreenContent`**

In the `ProfileScreenContent` signature, add (before `modifier: Modifier = Modifier`):

```kotlin
    accountState: AccountState,
    onLogoutClick: () -> Unit,
    onDeleteAccountConfirm: () -> Unit,
```

- [ ] **Step 4: Render the account card + dialog**

In `ProfileScreenContent`, inside the main `Column`, add the following just **before** the final `Spacer(modifier = Modifier.height(16.dp))`:

```kotlin
            // ── Account: logout + delete ───────────────────────────────────
            var showDeleteDialog by remember { mutableStateOf(false) }
            val isDeleting = accountState is AccountState.Deleting

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FieldLabel("חשבון")

                    OutlinedButton(
                        onClick = onLogoutClick,
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("התנתקות")
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("מחיקת חשבון ונתונים")
                    }

                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    if (accountState is AccountState.Error) {
                        Text(
                            text = accountState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                    title = { Text("מחיקת חשבון ונתונים") },
                    text = {
                        Text(
                            "פעולה זו תמחק לצמיתות את כל הנתונים שלך — ארוחות, נתוני בריאות, " +
                                "תובנות והפרופיל — וגם את החשבון. לא ניתן לבטל פעולה זו."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteAccountConfirm()
                            },
                            enabled = !isDeleting
                        ) {
                            Text("מחק לצמיתות", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                            Text("ביטול")
                        }
                    }
                )
            }
```

- [ ] **Step 5: Update the two `@Preview` composables**

In `ProfileScreenPreviewLight` (and any other preview calling `ProfileScreenContent`), add these arguments to the call:

```kotlin
            accountState = AccountState.Idle,
            onLogoutClick = {}, onDeleteAccountConfirm = {},
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt
git commit -m "feat: add account card with logout and delete-with-warning to Profile"
```

---

## Task 10: Wire callbacks in `Navigation.kt` (client)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/Navigation.kt`

- [ ] **Step 1: Provide context and the two callbacks to the Profile entry**

In `app/src/main/java/com/myhealthtracker/app/Navigation.kt`, replace the `entry<Profile> { ... }` block with:

```kotlin
        entry<Profile> {
          val context = LocalContext.current
          ProfileScreen(
            viewModel = profileViewModel,
            onSaveSuccess = {
              backStack.clear()
              backStack.add(Dashboard)
            },
            onLogout = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            },
            onAccountDeleted = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
```

(`LocalContext` is already imported in this file.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/Navigation.kt
git commit -m "feat: wire Profile logout and account-deletion navigation"
```

---

## Task 11: Full verification + docs

**Files:**
- Modify: `docs/CHANGELOG.md`

- [ ] **Step 1: Run the full client unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the new account + delete tests).

- [ ] **Step 2: Run the full functions suite + lint**

Run: `cd functions && npm run lint && npx jest`
Expected: no TS errors; all tests pass.

- [ ] **Step 3: Update the changelog**

Add an entry to `docs/CHANGELOG.md` (match the existing format/section style in that file):

```markdown
- Profile: added logout and permanent account + data deletion (with a warning dialog),
  backed by a new `deleteUserData` Cloud Function. Added a monthly `cleanupInactiveUsers`
  cron that deletes accounts inactive (no app open and no meals logged) for 30 days,
  using a new `lastActiveAt` heartbeat written on app foreground.
```

- [ ] **Step 4: Commit**

```bash
git add docs/CHANGELOG.md
git commit -m "docs: changelog for logout, account deletion, inactivity cleanup"
```

- [ ] **Step 5: STOP — manual deploy required (user action)**

Account deletion and the cleanup cron do not work until the functions are deployed.
Ask the user to run (they handle Firebase deploys per project rules):

```
firebase deploy --only functions
```

This publishes `deleteUserData` and `cleanupInactiveUsers`; the v2 `onSchedule` trigger
auto-creates the Cloud Scheduler job — no separate console step.

---

## Self-Review notes (for the executor)

- **Spec coverage:** purgeUser (§1) ✓ T1; deleteUserData callable (§2) ✓ T2; cleanup cron (§3) ✓ T3; lastActiveAt heartbeat (§4) ✓ T4/T8; AccountRepository (§5) ✓ T5; AppContainer (§5) ✓ T6; ViewModel state (§6) ✓ T7; UI + dialog (§7) ✓ T9; Navigation wiring (§8) ✓ T10; rules unchanged (§9) ✓ (verified `users/{uid}` write rule covers `lastActiveAt`); tests ✓ T1–T3, T5, T7; manual deploy ✓ T11.
- **Type consistency:** `AccountState.{Idle,Deleting,Deleted,Error}`, `accountState`, `deleteAccount()`, `ActivityRepository.touchLastActive(uid)`, `AccountRepository.deleteAccount()`, `mapDeleteAccountError`, `purgeUser(uid, deps)`, `handleDeleteUserData(auth, deps)`, `isInactive`/`lastActivitySignal`/`UserActivity` are used identically across tasks.
```
