import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { VertexAI } from "@google-cloud/vertexai";
import { validateRequest, ValidationError, AnalyzeMealRequest } from "./validation";
import {
  GEMINI_MODEL,
  buildMealSystemInstruction,
  MEAL_RESPONSE_SCHEMA,
  ProfileContext,
  mealTextPrompt,
  mealImagePrompt,
} from "./prompts";
import { parseGeminiResult, ParseError, MealResult } from "./parse";
import { callWithRetry, withTimeout } from "./vertexClient";

if (getApps().length === 0) initializeApp();

const LOCATION = process.env.VERTEX_LOCATION || "us-central1";
// Function deploy region is independent of the Vertex location; keep it aligned
// with the client's default callable region (us-central1) unless overridden.
const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";
const MODEL = GEMINI_MODEL;
const VERTEX_TIMEOUT_MS = 15000;

async function readProfile(uid: string): Promise<ProfileContext | null> {
  try {
    const snap = await getFirestore().doc(`users/${uid}`).get();
    const profile = snap.get("profile") as Record<string, unknown> | undefined;
    if (!profile) return null;
    return {
      weightKg: typeof profile.weightKg === "number" ? profile.weightKg : undefined,
      heightCm: typeof profile.heightCm === "number" ? profile.heightCm : undefined,
      gender: typeof profile.gender === "string" ? profile.gender : undefined,
    };
  } catch {
    return null; // best-effort
  }
}

async function runGemini(req: AnalyzeMealRequest, profile: ProfileContext | null): Promise<string> {
  const vertex = new VertexAI({ project: process.env.GCLOUD_PROJECT!, location: LOCATION });
  const model = vertex.getGenerativeModel({
    model: MODEL,
    generationConfig: { responseMimeType: "application/json", responseSchema: MEAL_RESPONSE_SCHEMA as never },
    systemInstruction: { role: "system", parts: [{ text: buildMealSystemInstruction(profile) }] },
  });

  const parts =
    req.inputType === "image"
      ? [
          { text: mealImagePrompt(req.text) },
          { inlineData: { mimeType: "image/jpeg", data: req.imageBase64! } },
        ]
      : [{ text: mealTextPrompt(req.text!) }];

  const result = await callWithRetry(
    () => withTimeout(model.generateContent({ contents: [{ role: "user", parts }] }), VERTEX_TIMEOUT_MS),
    { retries: 2, baseDelayMs: 500 }
  );
  return result.response.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
}

export function mapErrorToHttpsError(e: any): HttpsError {
  const code = e?.code;
  if (code === 429) {
    return new HttpsError("resource-exhausted", "AI service is busy. Please try again shortly.");
  }
  return new HttpsError("internal", "Could not analyze the meal. You can enter it manually.");
}

export const analyzeMeal = onCall(
  { enforceAppCheck: true, timeoutSeconds: 60, region: FUNCTION_REGION },
  async (request: CallableRequest): Promise<MealResult> => {
    const requestId = Math.random().toString(36).slice(2, 10);
    const started = Date.now();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }
    const uid = request.auth.uid;

    let parsedReq: AnalyzeMealRequest;
    try {
      parsedReq = validateRequest(request.data);
    } catch (e) {
      logger.warn("analyzeMeal invalid argument", { requestId, uid, message: (e as Error).message });
      throw new HttpsError("invalid-argument", (e as ValidationError).message);
    }

    try {
      const profile = await readProfile(uid);
      const raw = await runGemini(parsedReq, profile);
      const result = parseGeminiResult(raw);
      logger.info("analyzeMeal ok", {
        requestId, uid, model: MODEL, durationMs: Date.now() - started, itemCount: result.items.length,
      });
      return result;
    } catch (e) {
      const code = (e as { code?: number }).code;
      if (code === 429) {
        logger.error("analyzeMeal quota", { requestId, uid, durationMs: Date.now() - started });
      } else {
        logger.error("analyzeMeal failed", {
          requestId, uid, durationMs: Date.now() - started,
          kind: e instanceof ParseError ? "parse" : "vertex",
          message: (e as Error).message, // safe: our message, not raw image/keys
        });
      }
      throw mapErrorToHttpsError(e);
    }
  }
);
