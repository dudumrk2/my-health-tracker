import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { purgeUser, prodPurgeDeps } from "./account/purgeUser";

if (getApps().length === 0) initializeApp();

const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";
const TZ = process.env.INSIGHTS_TZ || "Asia/Jerusalem";
// Daily, 03:00 by default. Daily (vs monthly) keeps the actual deletion close to the
// INACTIVE_DAYS threshold instead of letting an inactive account linger up to a month longer.
const CLEANUP_SCHEDULE = process.env.CLEANUP_SCHEDULE || "0 3 * * *";

/** Parses the inactivity threshold from env, ignoring blank/non-numeric/non-positive values. */
export function parseInactiveDays(raw: string | undefined, fallback = 30): number {
  const n = Number(raw);
  return raw !== undefined && raw.trim() !== "" && Number.isFinite(n) && n > 0 ? n : fallback;
}

const INACTIVE_DAYS = parseInactiveDays(process.env.CLEANUP_INACTIVE_DAYS);

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

/**
 * True when the non-meal signals (app-open heartbeat, or profile creation as fallback) already
 * prove the user is active. Lets the caller skip the per-user meals query for active users, since
 * a meal can only push the signal more recent — never make an already-recent user inactive.
 */
export function activeByBaseSignal(
  base: { lastActiveAt?: number; createdAt?: number },
  nowMs: number,
  inactiveDays: number
): boolean {
  const signal = base.lastActiveAt ?? base.createdAt;
  if (signal === undefined) return false;
  return signal >= nowMs - inactiveDays * 24 * 60 * 60 * 1000;
}

/**
 * Iterates the `users` collection and purges anyone inactive for INACTIVE_DAYS.
 * A failure for one user is logged and skipped — never fatal for the rest.
 *
 * The user docs from the collection scan are reused directly (no per-user re-read), and the
 * meals subcollection is queried only for users the heartbeat/createdAt didn't already clear.
 */
async function runCleanup(nowMs: number): Promise<void> {
  const db = getFirestore();
  const users = await db.collection("users").get();
  let deleted = 0, kept = 0, failed = 0;
  const purgeDeps = prodPurgeDeps();
  for (const userDoc of users.docs) {
    try {
      const lastActiveAt = toMillis(userDoc.get("lastActiveAt"));
      const profile = userDoc.get("profile") as Record<string, unknown> | undefined;
      const createdAt = profile ? toMillis(profile.createdAt) : undefined;

      // Fast path: a recent heartbeat (or recent creation) proves activity without touching meals.
      if (activeByBaseSignal({ lastActiveAt, createdAt }, nowMs, INACTIVE_DAYS)) {
        kept++;
        continue;
      }

      // Otherwise a recent meal could still be the only sign of life.
      const mealSnap = await db
        .collection(`users/${userDoc.id}/meals`)
        .orderBy("loggedAt", "desc")
        .limit(1)
        .get();
      const lastMealAt = mealSnap.empty ? undefined : toMillis(mealSnap.docs[0].get("loggedAt"));
      const activity: UserActivity = { lastActiveAt, createdAt, lastMealAt };

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
