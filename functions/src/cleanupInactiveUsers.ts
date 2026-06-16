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

/** True only when we have at least one signal AND the newest is older than the threshold. */
export function isInactive(a: UserActivity, nowMs: number, inactiveDays: number): boolean {
  const hasSignal = a.lastActiveAt !== undefined || a.createdAt !== undefined || a.lastMealAt !== undefined;
  if (!hasSignal) return false; // never delete without positive evidence of inactivity
  const cutoff = nowMs - inactiveDays * 24 * 60 * 60 * 1000;
  return lastActivitySignal(a) < cutoff;
}

function toMillis(v: unknown): number | undefined {
  return v instanceof Timestamp ? v.toMillis() : undefined;
}

/** Reads the activity signals for one user from Firestore. */
async function readUserActivity(uid: string): Promise<UserActivity> {
  const db = getFirestore();
  const [userSnap, mealSnap] = await Promise.all([
    db.doc(`users/${uid}`).get(),
    db.collection(`users/${uid}/meals`).orderBy("loggedAt", "desc").limit(1).get(),
  ]);
  const profile = userSnap.get("profile") as Record<string, unknown> | undefined;
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
  const purgeDeps = prodPurgeDeps();
  for (const userDoc of users.docs) {
    try {
      const activity = await readUserActivity(userDoc.id);
      if (isInactive(activity, nowMs, INACTIVE_DAYS)) {
        await purgeUser(userDoc.id, purgeDeps);
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
