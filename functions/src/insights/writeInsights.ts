import { ParsedInsights } from "./insightsParse";

export type WriteMode = "evening" | "todayOnly";

/** Minimal Firestore write surface, kept narrow so the merge behavior is unit-testable. */
export interface DocWriter {
  set(data: Record<string, unknown>, options: { merge: boolean }): Promise<unknown>;
}
export interface InsightsDb {
  doc(path: string): DocWriter;
}

/** Returns the yyyy-MM-dd date one day after `date` (UTC, boundary-safe). */
export function nextDate(date: string): string {
  const d = new Date(`${date}T00:00:00.000Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

/**
 * Idempotent, field-level-merge write.
 *
 * - `todayOnly`: writes ONLY the `today` block to insights/{date}; the `tomorrow`
 *   block authored the previous evening is preserved by the merge.
 * - `evening`: writes `today` → insights/{date} AND `tomorrow` → insights/{date+1},
 *   so a given day's document carries last night's `tomorrow` and the day's `today`.
 */
export async function writeInsights(
  db: InsightsDb,
  uid: string,
  date: string,
  parsed: ParsedInsights,
  mode: WriteMode,
  trigger: string,
  generatedAt: unknown
): Promise<void> {
  const todayDoc = {
    date,
    today: parsed.today,
    disclaimer: parsed.disclaimer,
    trigger,
    generatedAt,
  };
  await db.doc(`users/${uid}/insights/${date}`).set(todayDoc, { merge: true });

  if (mode === "evening") {
    const next = nextDate(date);
    const tomorrowDoc = {
      date: next,
      tomorrow: parsed.tomorrow,
      disclaimer: parsed.disclaimer,
      trigger,
      generatedAt,
    };
    await db.doc(`users/${uid}/insights/${next}`).set(tomorrowDoc, { merge: true });
  }
}
