import { onSchedule } from "firebase-functions/v2/scheduler";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { VertexAI } from "@google-cloud/vertexai";
import { callWithRetry, withTimeout } from "./vertexClient";
import { fetchDayData } from "./insights/aggregate";
import { RESPONSE_SCHEMA } from "./insights/insightsPrompt";
import { ParsedInsights } from "./insights/insightsParse";
import { writeInsights, WriteMode, InsightsDb } from "./insights/writeInsights";
import { runInsightsForUser, InsightsRunDeps } from "./insights/core";

if (getApps().length === 0) initializeApp();

const LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";
const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const TZ = process.env.INSIGHTS_TZ || "Asia/Jerusalem";
const EVENING_SCHEDULE = process.env.INSIGHTS_EVENING_SCHEDULE || "0 21 * * *";
const MIDDAY_SCHEDULE = process.env.INSIGHTS_MIDDAY_SCHEDULE || "0 15 * * *";
const VERTEX_TIMEOUT_MS = 15000;

/** yyyy-MM-dd for "today" in the configured timezone. */
function todayInTz(now: Date = new Date()): string {
  // en-CA yields ISO-style yyyy-MM-dd; honor the scheduling timezone.
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(now);
}

/** Production Gemini call. `generate(system, user)` builds the model per request and returns raw text. */
async function geminiGenerate(systemInstruction: string, userPrompt: string): Promise<string> {
  const vertex = new VertexAI({ project: process.env.GCLOUD_PROJECT!, location: LOCATION });
  const model = vertex.getGenerativeModel({
    model: MODEL,
    generationConfig: { responseMimeType: "application/json", responseSchema: RESPONSE_SCHEMA as never },
    systemInstruction: { role: "system", parts: [{ text: systemInstruction }] },
  });
  const result = await callWithRetry(
    () =>
      withTimeout(
        model.generateContent({ contents: [{ role: "user", parts: [{ text: userPrompt }] }] }),
        VERTEX_TIMEOUT_MS
      ),
    { retries: 2, baseDelayMs: 500 }
  );
  return result.response.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
}

/** Production write: adapts admin Firestore to the testable InsightsDb and stamps generatedAt server-side. */
async function geminiWrite(
  uid: string,
  date: string,
  parsed: ParsedInsights,
  mode: WriteMode,
  trigger: string
): Promise<void> {
  const db = getFirestore();
  const adapter: InsightsDb = { doc: (path: string) => db.doc(path) };
  await writeInsights(adapter, uid, date, parsed, mode, trigger, FieldValue.serverTimestamp());
}

function prodDeps(): InsightsRunDeps {
  return { fetchDayData, generate: geminiGenerate, write: geminiWrite };
}

/**
 * Iterates the `users` collection (no auth context in scheduled runs). A failure
 * for one user is logged and skipped — never fatal for the rest.
 */
async function runForAllUsers(date: string, mode: WriteMode, trigger: string, skipEmpty: boolean): Promise<void> {
  const users = await getFirestore().collection("users").get();
  let written = 0, skipped = 0, failed = 0;
  for (const userDoc of users.docs) {
    try {
      const r = await runInsightsForUser(userDoc.id, date, { mode, trigger, skipEmpty }, prodDeps());
      if (r.status === "written") written++;
      else if (r.status === "skipped") skipped++;
      else failed++;
    } catch (e) {
      failed++;
      logger.error("insights user iteration error", { uid: userDoc.id, trigger, message: (e as Error).message });
    }
  }
  logger.info("insights batch complete", { trigger, date, users: users.size, written, skipped, failed });
}

export function mapInsightsError(e: unknown): HttpsError {
  const code = (e as { code?: number })?.code;
  if (code === 429) {
    return new HttpsError("resource-exhausted", "שירות ה-AI עמוס כרגע. נסה שוב בעוד רגע.");
  }
  return new HttpsError("internal", "לא ניתן לרענן את התובנות כרגע. נסה שוב מאוחר יותר.");
}

// Evening run: authors today→{D} and tomorrow→{D+1}. Always runs (must produce tomorrow prep).
export const generateInsightsEvening = onSchedule(
  { schedule: EVENING_SCHEDULE, timeZone: TZ, region: FUNCTION_REGION, timeoutSeconds: 540 },
  async () => {
    await runForAllUsers(todayInTz(), "evening", "evening", false);
  }
);

// Midday (15:00) run: updates only today→{D} when the day has data; preserves last night's tomorrow.
export const generateInsightsMidday = onSchedule(
  { schedule: MIDDAY_SCHEDULE, timeZone: TZ, region: FUNCTION_REGION, timeoutSeconds: 540 },
  async () => {
    await runForAllUsers(todayInTz(), "todayOnly", "midday", true);
  }
);

// Manual refresh: on-demand update of today for the authenticated user.
export const generateInsightsManual = onCall(
  { enforceAppCheck: true, timeoutSeconds: 60, region: FUNCTION_REGION },
  async (request: CallableRequest): Promise<{ status: string }> => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "נדרשת התחברות.");
    }
    const uid = request.auth.uid;
    const date = todayInTz();
    try {
      const r = await runInsightsForUser(uid, date, { mode: "todayOnly", trigger: "manual", skipEmpty: false }, prodDeps());
      if (r.status === "failed") {
        throw new HttpsError("internal", "לא ניתן לרענן את התובנות כרגע. נסה שוב מאוחר יותר.");
      }
      return { status: r.status };
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      throw mapInsightsError(e);
    }
  }
);
