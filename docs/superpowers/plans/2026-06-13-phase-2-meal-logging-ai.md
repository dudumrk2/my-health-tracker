# Phase 2 — Meal Logging + AI Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user log a meal by free text or photo, analyze it with Gemini (server-side only via a Cloud Function), edit the structured result, and save it to Firestore — plus a quick "+ water" action.

**Architecture:** A new TypeScript Cloud Function (`analyzeMeal`, 2nd gen, App Check + Auth gated) calls Vertex AI and returns `{ items[], totals, lowConfidence }` without persisting anything. The Android client shows the result for editing, then writes `users/{uid}/meals/{mealId}` itself. Mock repositories are replaced by Firestore-backed implementations resolved through a lightweight service locator.

**Tech Stack:** Cloud Functions for Firebase (2nd gen, TypeScript, Node 20), `@google-cloud/vertexai`, Jest; Android Kotlin + Jetpack Compose, Firebase Functions/App Check SDKs, MockK + JUnit4, Firebase Local Emulator Suite.

**Branch:** `feat/phase-2-meal-ai` (already created).

**Spec:** `docs/superpowers/specs/2026-06-13-phase-2-meal-logging-ai-design.md`

---

## File Structure

### Backend (new, repo root)
- `firebase.json` — emulator + functions + firestore config.
- `.firebaserc` — project alias placeholder (user fills real project id).
- `firestore.rules` — per-user access rules.
- `functions/package.json`, `functions/tsconfig.json`, `functions/.gitignore`, `functions/jest.config.js`
- `functions/src/validation.ts` — request validation (pure).
- `functions/src/prompt.ts` — Contract A prompt + response schema (pure).
- `functions/src/parse.ts` — parse Gemini JSON → result, recompute totals (pure).
- `functions/src/vertexClient.ts` — Vertex call with timeout + retry.
- `functions/src/analyzeMeal.ts` — the callable, wiring the above.
- `functions/src/index.ts` — exports.
- `functions/test/validation.test.ts`, `prompt.test.ts`, `parse.test.ts`, `vertexClient.test.ts`

### Android client
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — new deps.
- `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt` — Application (App Check + container init).
- `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt` — service locator.
- `app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalyzer.kt` — analyzer interface + result types.
- `app/src/main/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzer.kt` — callable impl.
- `app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt`
- `app/src/main/java/com/myhealthtracker/app/data/water/FirestoreWaterRepository.kt`
- `app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt` — downscale + base64.
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt`
- Modify: `app/src/main/AndroidManifest.xml` (FileProvider), `app/src/main/res/xml/file_paths.xml`
- Tests: `app/src/test/java/.../meal/AddMealViewModelTest.kt`, `.../meal/FunctionsMealAnalyzerTest.kt`
- Instrumented: `app/src/androidTest/java/.../FirestoreMealWaterEmulatorTest.kt`

### Docs
- `CLAUDE.md` (water schema), `docs/CHANGELOG.md`.

---

## PART A — Cloud Functions backend

### Task 1: Scaffold the functions project

**Files:**
- Create: `functions/package.json`, `functions/tsconfig.json`, `functions/.gitignore`, `functions/jest.config.js`
- Create: `firebase.json`, `.firebaserc`, `firestore.rules`

- [ ] **Step 1: Create `functions/package.json`**

```json
{
  "name": "functions",
  "scripts": {
    "build": "tsc",
    "lint": "tsc --noEmit",
    "test": "jest",
    "serve": "npm run build && firebase emulators:start --only functions"
  },
  "engines": { "node": "20" },
  "main": "lib/index.js",
  "dependencies": {
    "@google-cloud/vertexai": "^1.9.0",
    "firebase-admin": "^12.6.0",
    "firebase-functions": "^6.1.0"
  },
  "devDependencies": {
    "@types/jest": "^29.5.12",
    "jest": "^29.7.0",
    "ts-jest": "^29.2.5",
    "typescript": "^5.6.3"
  },
  "private": true
}
```

- [ ] **Step 2: Create `functions/tsconfig.json`**

```json
{
  "compilerOptions": {
    "module": "commonjs",
    "target": "es2021",
    "noImplicitReturns": true,
    "noUnusedLocals": true,
    "outDir": "lib",
    "sourceMap": true,
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "compileOnSave": true,
  "include": ["src"]
}
```

- [ ] **Step 3: Create `functions/jest.config.js`**

```js
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["**/test/**/*.test.ts"],
};
```

- [ ] **Step 4: Create `functions/.gitignore`**

```
node_modules/
lib/
.runtimeconfig.json
```

- [ ] **Step 5: Create `firebase.json`**

```json
{
  "functions": [
    {
      "source": "functions",
      "codebase": "default",
      "predeploy": ["npm --prefix \"$RESOURCE_DIR\" run build"]
    }
  ],
  "firestore": {
    "rules": "firestore.rules"
  },
  "emulators": {
    "functions": { "port": 5001 },
    "firestore": { "port": 8080 },
    "auth": { "port": 9099 },
    "ui": { "enabled": true }
  }
}
```

- [ ] **Step 6: Create `.firebaserc` (placeholder — user sets real project id)**

```json
{
  "projects": {
    "default": "my-health-tracker"
  }
}
```

- [ ] **Step 7: Create `firestore.rules`**

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
      match /{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }
    }
  }
}
```

- [ ] **Step 8: Install dependencies**

Run: `npm --prefix functions install`
Expected: dependencies installed, `functions/node_modules` created, no errors.

- [ ] **Step 9: Commit**

```bash
git add functions/package.json functions/tsconfig.json functions/jest.config.js functions/.gitignore firebase.json .firebaserc firestore.rules functions/package-lock.json
git commit -m "chore: scaffold Cloud Functions (TS) project + firestore rules"
```

---

### Task 2: Request validation (pure, TDD)

**Files:**
- Create: `functions/src/validation.ts`
- Test: `functions/test/validation.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { validateRequest, ValidationError } from "../src/validation";

describe("validateRequest", () => {
  it("accepts a valid text request", () => {
    const r = validateRequest({ inputType: "text", text: "2 eggs", date: "2026-06-13" });
    expect(r.inputType).toBe("text");
    expect(r.text).toBe("2 eggs");
    expect(r.date).toBe("2026-06-13");
  });

  it("accepts a valid image request", () => {
    const r = validateRequest({ inputType: "image", imageBase64: "abc", date: "2026-06-13" });
    expect(r.inputType).toBe("image");
    expect(r.imageBase64).toBe("abc");
  });

  it("rejects unknown inputType", () => {
    expect(() => validateRequest({ inputType: "audio", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects text mode with empty text", () => {
    expect(() => validateRequest({ inputType: "text", text: "   ", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects image mode with missing imageBase64", () => {
    expect(() => validateRequest({ inputType: "image", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects a bad date format", () => {
    expect(() => validateRequest({ inputType: "text", text: "x", date: "13/06/2026" })).toThrow(ValidationError);
  });

  it("rejects a null payload", () => {
    expect(() => validateRequest(null)).toThrow(ValidationError);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix functions test -- validation`
Expected: FAIL — cannot find module `../src/validation`.

- [ ] **Step 3: Write the implementation**

```ts
// functions/src/validation.ts
export class ValidationError extends Error {}

export interface AnalyzeMealRequest {
  inputType: "text" | "image";
  text?: string;
  imageBase64?: string;
  date: string;
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function validateRequest(data: unknown): AnalyzeMealRequest {
  if (typeof data !== "object" || data === null) {
    throw new ValidationError("Request body is missing.");
  }
  const d = data as Record<string, unknown>;

  const inputType = d.inputType;
  if (inputType !== "text" && inputType !== "image") {
    throw new ValidationError("inputType must be 'text' or 'image'.");
  }

  const date = d.date;
  if (typeof date !== "string" || !DATE_RE.test(date)) {
    throw new ValidationError("date must be in yyyy-MM-dd format.");
  }

  if (inputType === "text") {
    const text = d.text;
    if (typeof text !== "string" || text.trim().length === 0) {
      throw new ValidationError("text is required for text input.");
    }
    return { inputType, text: text.trim(), date };
  }

  const imageBase64 = d.imageBase64;
  if (typeof imageBase64 !== "string" || imageBase64.trim().length === 0) {
    throw new ValidationError("imageBase64 is required for image input.");
  }
  return { inputType, imageBase64, date };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix functions test -- validation`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/validation.ts functions/test/validation.test.ts
git commit -m "feat(functions): add analyzeMeal request validation"
```

---

### Task 3: Prompt + response schema (pure, TDD)

**Files:**
- Create: `functions/src/prompt.ts`
- Test: `functions/test/prompt.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { buildSystemInstruction, RESPONSE_SCHEMA, ProfileContext } from "../src/prompt";

describe("buildSystemInstruction", () => {
  it("includes Contract A safety constraints", () => {
    const s = buildSystemInstruction(null);
    expect(s).toMatch(/JSON/i);
    expect(s).toMatch(/items/);
    expect(s).toMatch(/lowConfidence/);
    expect(s.toLowerCase()).toContain("empty");
  });

  it("includes profile context when provided", () => {
    const p: ProfileContext = { weightKg: 80, heightCm: 180, gender: "male" };
    const s = buildSystemInstruction(p);
    expect(s).toContain("80");
    expect(s).toContain("180");
  });
});

describe("RESPONSE_SCHEMA", () => {
  it("requires items array and lowConfidence", () => {
    expect(RESPONSE_SCHEMA.properties.items).toBeDefined();
    expect(RESPONSE_SCHEMA.properties.lowConfidence).toBeDefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix functions test -- prompt`
Expected: FAIL — cannot find module `../src/prompt`.

- [ ] **Step 3: Write the implementation**

```ts
// functions/src/prompt.ts
import { SchemaType } from "@google-cloud/vertexai";

export interface ProfileContext {
  weightKg?: number;
  heightCm?: number;
  gender?: string;
}

export const RESPONSE_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    items: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        properties: {
          name: { type: SchemaType.STRING },
          quantity: { type: SchemaType.STRING },
          calories: { type: SchemaType.NUMBER },
          proteinG: { type: SchemaType.NUMBER },
          carbsG: { type: SchemaType.NUMBER },
          fatG: { type: SchemaType.NUMBER },
        },
        required: ["name", "quantity", "calories", "proteinG", "carbsG", "fatG"],
      },
    },
    lowConfidence: { type: SchemaType.BOOLEAN },
  },
  required: ["items", "lowConfidence"],
} as const;

export function buildSystemInstruction(profile: ProfileContext | null): string {
  const profileLine = profile
    ? `User context for portion estimation: weight ${profile.weightKg ?? "?"} kg, height ${profile.heightCm ?? "?"} cm, gender ${profile.gender ?? "?"}.`
    : "No user profile available.";

  return [
    "You are a nutrition analyzer. Identify food items and estimate their nutritional values.",
    profileLine,
    "Rules:",
    "- Respond with JSON only, matching the provided schema. No markdown, no wrapping text.",
    "- Use metric units. Quantity is a short human-readable string (e.g. '150 g', '1 cup').",
    "- Estimate values when uncertain; set lowConfidence=true when the identification or amounts are uncertain.",
    "- If the input (text or image) contains no food, return an empty items array and lowConfidence=false.",
    "- Do NOT diagnose, give medical advice, or recommend diets. Only identify and estimate.",
  ].join("\n");
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix functions test -- prompt`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add functions/src/prompt.ts functions/test/prompt.test.ts
git commit -m "feat(functions): add Contract A prompt and response schema"
```

---

### Task 4: Parse Gemini output + recompute totals (pure, TDD)

**Files:**
- Create: `functions/src/parse.ts`
- Test: `functions/test/parse.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { parseGeminiResult, ParseError } from "../src/parse";

describe("parseGeminiResult", () => {
  it("parses valid JSON and recomputes totals from items", () => {
    const raw = JSON.stringify({
      items: [
        { name: "Egg", quantity: "2", calories: 140, proteinG: 12, carbsG: 1, fatG: 10 },
        { name: "Toast", quantity: "1 slice", calories: 80, proteinG: 3, carbsG: 15, fatG: 1 },
      ],
      lowConfidence: false,
    });
    const r = parseGeminiResult(raw);
    expect(r.items).toHaveLength(2);
    expect(r.totals).toEqual({ calories: 220, proteinG: 15, carbsG: 16, fatG: 11 });
    expect(r.lowConfidence).toBe(false);
  });

  it("handles no-food result as empty items with zero totals", () => {
    const r = parseGeminiResult(JSON.stringify({ items: [], lowConfidence: false }));
    expect(r.items).toHaveLength(0);
    expect(r.totals).toEqual({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 });
  });

  it("throws ParseError on non-JSON output", () => {
    expect(() => parseGeminiResult("here is your meal: ...")).toThrow(ParseError);
  });

  it("throws ParseError when items is missing", () => {
    expect(() => parseGeminiResult(JSON.stringify({ lowConfidence: true }))).toThrow(ParseError);
  });

  it("coerces missing numeric fields to 0 and missing lowConfidence to false", () => {
    const raw = JSON.stringify({ items: [{ name: "X", quantity: "1" }] });
    const r = parseGeminiResult(raw);
    expect(r.items[0].calories).toBe(0);
    expect(r.lowConfidence).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix functions test -- parse`
Expected: FAIL — cannot find module `../src/parse`.

- [ ] **Step 3: Write the implementation**

```ts
// functions/src/parse.ts
export class ParseError extends Error {}

export interface MealItem {
  name: string;
  quantity: string;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface MealTotals {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface MealResult {
  items: MealItem[];
  totals: MealTotals;
  lowConfidence: boolean;
}

function num(v: unknown): number {
  return typeof v === "number" && isFinite(v) ? Math.round(v) : 0;
}

function str(v: unknown): string {
  return typeof v === "string" ? v : "";
}

export function parseGeminiResult(raw: string): MealResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new ParseError("Model did not return valid JSON.");
  }
  if (typeof parsed !== "object" || parsed === null) {
    throw new ParseError("Model output is not an object.");
  }
  const obj = parsed as Record<string, unknown>;
  if (!Array.isArray(obj.items)) {
    throw new ParseError("Model output is missing items array.");
  }

  const items: MealItem[] = obj.items.map((it) => {
    const o = (it ?? {}) as Record<string, unknown>;
    return {
      name: str(o.name),
      quantity: str(o.quantity),
      calories: num(o.calories),
      proteinG: num(o.proteinG),
      carbsG: num(o.carbsG),
      fatG: num(o.fatG),
    };
  });

  const totals = items.reduce<MealTotals>(
    (acc, it) => ({
      calories: acc.calories + it.calories,
      proteinG: acc.proteinG + it.proteinG,
      carbsG: acc.carbsG + it.carbsG,
      fatG: acc.fatG + it.fatG,
    }),
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
  );

  return { items, totals, lowConfidence: obj.lowConfidence === true };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix functions test -- parse`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/parse.ts functions/test/parse.test.ts
git commit -m "feat(functions): parse Gemini output and recompute totals server-side"
```

---

### Task 5: Vertex call with timeout + retry (TDD)

**Files:**
- Create: `functions/src/vertexClient.ts`
- Test: `functions/test/vertexClient.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { callWithRetry, isRetryable, TimeoutError } from "../src/vertexClient";

describe("isRetryable", () => {
  it("treats 429/503/timeout as retryable", () => {
    expect(isRetryable({ code: 429 })).toBe(true);
    expect(isRetryable({ code: 503 })).toBe(true);
    expect(isRetryable(new TimeoutError())).toBe(true);
  });
  it("treats other errors as non-retryable", () => {
    expect(isRetryable({ code: 400 })).toBe(false);
    expect(isRetryable(new Error("boom"))).toBe(false);
  });
});

describe("callWithRetry", () => {
  it("returns on first success", async () => {
    const fn = jest.fn().mockResolvedValue("ok");
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).resolves.toBe("ok");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("retries retryable errors up to the limit then throws", async () => {
    const fn = jest.fn().mockRejectedValue({ code: 503 });
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).rejects.toEqual({ code: 503 });
    expect(fn).toHaveBeenCalledTimes(3); // 1 + 2 retries
  });

  it("does not retry non-retryable errors", async () => {
    const fn = jest.fn().mockRejectedValue({ code: 400 });
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).rejects.toEqual({ code: 400 });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("succeeds after a transient failure", async () => {
    const fn = jest.fn().mockRejectedValueOnce({ code: 429 }).mockResolvedValue("ok");
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).resolves.toBe("ok");
    expect(fn).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix functions test -- vertexClient`
Expected: FAIL — cannot find module `../src/vertexClient`.

- [ ] **Step 3: Write the implementation**

```ts
// functions/src/vertexClient.ts
export class TimeoutError extends Error {
  constructor() {
    super("Vertex call timed out.");
  }
}

export function isRetryable(err: unknown): boolean {
  if (err instanceof TimeoutError) return true;
  const code = (err as { code?: number })?.code;
  return code === 429 || code === 503;
}

export function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new TimeoutError()), ms);
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e) => {
        clearTimeout(timer);
        reject(e);
      }
    );
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function callWithRetry<T>(
  fn: () => Promise<T>,
  opts: { retries: number; baseDelayMs: number }
): Promise<T> {
  let attempt = 0;
  for (;;) {
    try {
      return await fn();
    } catch (err) {
      if (attempt >= opts.retries || !isRetryable(err)) throw err;
      await sleep(opts.baseDelayMs * Math.pow(2, attempt));
      attempt++;
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix functions test -- vertexClient`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/vertexClient.ts functions/test/vertexClient.test.ts
git commit -m "feat(functions): add Vertex timeout + retry helper"
```

---

### Task 6: Wire the `analyzeMeal` callable

**Files:**
- Create: `functions/src/analyzeMeal.ts`, `functions/src/index.ts`

No new unit test (the pure logic is already covered; this file is integration glue verified by `tsc` build and, later, the emulator). Keep it thin.

- [ ] **Step 1: Write `functions/src/analyzeMeal.ts`**

```ts
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { VertexAI } from "@google-cloud/vertexai";
import { validateRequest, ValidationError, AnalyzeMealRequest } from "./validation";
import { buildSystemInstruction, RESPONSE_SCHEMA, ProfileContext } from "./prompt";
import { parseGeminiResult, ParseError, MealResult } from "./parse";
import { callWithRetry, withTimeout } from "./vertexClient";

if (getApps().length === 0) initializeApp();

const LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
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
    generationConfig: { responseMimeType: "application/json", responseSchema: RESPONSE_SCHEMA as never },
    systemInstruction: { role: "system", parts: [{ text: buildSystemInstruction(profile) }] },
  });

  const parts =
    req.inputType === "image"
      ? [
          { text: "Analyze the food in this image." },
          { inlineData: { mimeType: "image/jpeg", data: req.imageBase64! } },
        ]
      : [{ text: `Analyze this meal description: ${req.text}` }];

  const result = await callWithRetry(
    () => withTimeout(model.generateContent({ contents: [{ role: "user", parts }] }), VERTEX_TIMEOUT_MS),
    { retries: 2, baseDelayMs: 500 }
  );
  return result.response.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
}

export const analyzeMeal = onCall(
  { enforceAppCheck: true, timeoutSeconds: 60, region: LOCATION },
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
        throw new HttpsError("resource-exhausted", "AI service is busy. Please try again shortly.");
      }
      logger.error("analyzeMeal failed", {
        requestId, uid, durationMs: Date.now() - started,
        kind: e instanceof ParseError ? "parse" : "vertex",
        message: (e as Error).message, // safe: our message, not raw image/keys
      });
      throw new HttpsError("internal", "Could not analyze the meal. You can enter it manually.");
    }
  }
);
```

- [ ] **Step 2: Write `functions/src/index.ts`**

```ts
export { analyzeMeal } from "./analyzeMeal";
```

- [ ] **Step 3: Build to verify it compiles**

Run: `npm --prefix functions run build`
Expected: `tsc` completes with no errors; `functions/lib/` is generated.

- [ ] **Step 4: Run full functions test suite**

Run: `npm --prefix functions test`
Expected: PASS (all suites from Tasks 2–5).

- [ ] **Step 5: Commit**

```bash
git add functions/src/analyzeMeal.ts functions/src/index.ts
git commit -m "feat(functions): wire analyzeMeal callable (App Check, profile, Vertex, error envelope)"
```

> **STOP / MANUAL STEP after this task is reachable:** Do **not** run `firebase deploy --only functions`, enable the Vertex AI API, or configure App Check — these are the user's manual operations. Pause and hand off before any deploy.

---

## PART B — Android client

### Task 7: Add Functions + App Check dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:71-75`

- [ ] **Step 1: Add library entries to `gradle/libs.versions.toml`** (under `[libraries]`, after `firebase-firestore`)

```toml
firebase-functions = { group = "com.google.firebase", name = "firebase-functions" }
firebase-appcheck-playintegrity = { group = "com.google.firebase", name = "firebase-appcheck-playintegrity" }
firebase-appcheck-debug = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
```

- [ ] **Step 2: Add dependencies in `app/build.gradle.kts`** (in the Firebase block, after `implementation(libs.play.services.auth)`)

```kotlin
  implementation(libs.firebase.functions)
  implementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)
```

- [ ] **Step 3: Sync/build to verify resolution**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath` (or `.\gradlew.bat` on Windows PowerShell)
Expected: `firebase-functions` and `firebase-appcheck` artifacts resolve with no errors.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Firebase Functions + App Check dependencies"
```

---

### Task 8: Application class + App Check + service locator

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt`
- Modify: `app/src/main/AndroidManifest.xml:13` (add `android:name`)

> Note: `FirestoreMealRepository` / `FirestoreWaterRepository` are created in Tasks 10–11. To keep this task compiling on its own, `AppContainer` initially points meal/water at `FakeRepository` and is switched to the Firestore impls at the end of Task 11. This is called out again there.

- [ ] **Step 1: Create `AppContainer.kt`**

```kotlin
package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.WaterRepository

object AppContainer {
    // Swapped to Firestore-backed implementations in Task 11.
    var mealRepository: MealRepository = FakeRepository
        internal set
    var waterRepository: WaterRepository = FakeRepository
        internal set
}
```

- [ ] **Step 2: Create `MyHealthApp.kt`**

```kotlin
package com.myhealthtracker.app

import android.app.Application
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase

class MyHealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val factory = if (BuildConfigCompat.isDebug(this)) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }
}

private object BuildConfigCompat {
    // buildConfig is disabled in this module; detect debuggable flag at runtime instead.
    fun isDebug(app: Application): Boolean {
        val flags = app.applicationInfo.flags
        return (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
```

- [ ] **Step 3: Register the Application in the manifest**

In `app/src/main/AndroidManifest.xml`, add `android:name=".MyHealthApp"` to the `<application>` tag:

```xml
    <application
        android:name=".MyHealthApp"
        android:allowBackup="true"
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add Application class with App Check + AppContainer service locator"
```

---

### Task 9: MealAnalyzer interface + Functions implementation (TDD)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalyzer.kt`
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzer.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzerTest.kt`

The analyzer is split so the network call is behind an interface the ViewModel and tests can fake. `FunctionsMealAnalyzer` does the actual callable; the response-mapping logic is a pure function we unit-test.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.myhealthtracker.app.data.meal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionsMealAnalyzerTest {

    @Test
    fun `maps callable map response to result`() {
        val raw = mapOf(
            "items" to listOf(
                mapOf("name" to "Egg", "quantity" to "2", "calories" to 140.0,
                      "proteinG" to 12.0, "carbsG" to 1.0, "fatG" to 10.0)
            ),
            "totals" to mapOf("calories" to 140.0, "proteinG" to 12.0, "carbsG" to 1.0, "fatG" to 10.0),
            "lowConfidence" to true
        )
        val result = mapAnalyzeResponse(raw)
        assertEquals(1, result.items.size)
        assertEquals("Egg", result.items[0].name)
        assertEquals(140, result.items[0].calories)
        assertEquals(140, result.totals.calories)
        assertTrue(result.lowConfidence)
    }

    @Test
    fun `empty items maps to empty result with zero totals`() {
        val raw = mapOf("items" to emptyList<Any>(), "lowConfidence" to false)
        val result = mapAnalyzeResponse(raw)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totals.calories)
        assertEquals(false, result.lowConfidence)
    }

    @Test(expected = MealAnalysisException::class)
    fun `missing items throws analysis exception`() {
        mapAnalyzeResponse(mapOf("lowConfidence" to false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FunctionsMealAnalyzerTest"`
Expected: FAIL — unresolved references `mapAnalyzeResponse`, `MealAnalysisException`.

- [ ] **Step 3: Create `MealAnalyzer.kt`**

```kotlin
package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals

data class MealAnalysisResult(
    val items: List<MealItem>,
    val totals: MealTotals,
    val lowConfidence: Boolean
)

class MealAnalysisException(message: String) : Exception(message)

interface MealAnalyzer {
    /** Analyze a meal from text or image (base64). Throws MealAnalysisException on failure. */
    suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult
}
```

- [ ] **Step 4: Create `FunctionsMealAnalyzer.kt`**

```kotlin
package com.myhealthtracker.app.data.meal

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.tasks.await

private fun anyToInt(v: Any?): Int = when (v) {
    is Number -> v.toInt()
    else -> 0
}

private fun anyToString(v: Any?): String = v as? String ?: ""

/** Pure mapping of the callable's raw response map to a typed result. */
fun mapAnalyzeResponse(raw: Map<*, *>): MealAnalysisResult {
    val itemsRaw = raw["items"] as? List<*>
        ?: throw MealAnalysisException("Response missing items.")
    val items = itemsRaw.map { entry ->
        val m = entry as? Map<*, *> ?: emptyMap<Any, Any>()
        MealItem(
            name = anyToString(m["name"]),
            quantity = anyToString(m["quantity"]),
            calories = anyToInt(m["calories"]),
            proteinG = anyToInt(m["proteinG"]),
            carbsG = anyToInt(m["carbsG"]),
            fatG = anyToInt(m["fatG"])
        )
    }
    val totals = MealTotals(
        calories = items.sumOf { it.calories },
        proteinG = items.sumOf { it.proteinG },
        carbsG = items.sumOf { it.carbsG },
        fatG = items.sumOf { it.fatG }
    )
    return MealAnalysisResult(items, totals, raw["lowConfidence"] == true)
}

class FunctionsMealAnalyzer(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : MealAnalyzer {

    override suspend fun analyze(
        inputType: String, text: String?, imageBase64: String?, date: String
    ): MealAnalysisResult {
        val payload = buildMap<String, Any> {
            put("inputType", inputType)
            put("date", date)
            if (text != null) put("text", text)
            if (imageBase64 != null) put("imageBase64", imageBase64)
        }
        try {
            val response = functions.getHttpsCallable("analyzeMeal").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val raw = response.data as? Map<*, *>
                ?: throw MealAnalysisException("Unexpected response.")
            return mapAnalyzeResponse(raw)
        } catch (e: FirebaseFunctionsException) {
            throw MealAnalysisException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                        "שירות ה-AI עמוס כרגע. נסה שוב בעוד רגע."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "נדרשת התחברות מחדש."
                    else -> "לא ניתן לנתח את הארוחה. אפשר להזין ידנית."
                }
            )
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*FunctionsMealAnalyzerTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalyzer.kt app/src/main/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzer.kt app/src/test/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzerTest.kt
git commit -m "feat: add MealAnalyzer interface + Functions-backed implementation"
```

---

### Task 10: FirestoreMealRepository

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt`

This maps Firestore docs ↔ `MealEntry` and keeps a hot `meals` StateFlow via a snapshot listener scoped to the signed-in user. Logic-light; verified by build + the emulator test in Task 12.

- [ ] **Step 1: Create `FirestoreMealRepository.kt`**

```kotlin
package com.myhealthtracker.app.data.meal

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class FirestoreMealRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : MealRepository {

    private val _meals = MutableStateFlow<List<MealEntry>>(emptyList())
    override val meals: StateFlow<List<MealEntry>> = _meals.asStateFlow()

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) attachListener(uid) else _meals.value = emptyList()
        }
        auth.currentUser?.uid?.let { attachListener(it) }
    }

    private fun attachListener(uid: String) {
        firestore.collection("users").document(uid).collection("meals")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _meals.value = snap.documents.mapNotNull { doc -> doc.toMealEntry() }
                    .sortedByDescending { it.loggedAt }
            }
    }

    override fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "date" to date,
            "loggedAt" to Timestamp.now(),
            "inputType" to inputType,
            "description" to description,
            "items" to items.map { it.toMap() },
            "totals" to totals.toMap(),
            "aiModel" to "gemini-2.5-flash"
        )
        firestore.collection("users").document(uid).collection("meals").add(data)
    }

    override fun deleteMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).collection("meals").document(mealId).delete()
    }
}

private fun MealItem.toMap() = mapOf(
    "name" to name, "quantity" to quantity, "calories" to calories,
    "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun MealTotals.toMap() = mapOf(
    "calories" to calories, "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun com.google.firebase.firestore.DocumentSnapshot.toMealEntry(): MealEntry? {
    val date = getString("date") ?: return null
    val itemsRaw = get("items") as? List<*> ?: emptyList<Any>()
    val items = itemsRaw.mapNotNull { e ->
        val m = e as? Map<*, *> ?: return@mapNotNull null
        MealItem(
            name = m["name"] as? String ?: "",
            quantity = m["quantity"] as? String ?: "",
            calories = (m["calories"] as? Number)?.toInt() ?: 0,
            proteinG = (m["proteinG"] as? Number)?.toInt() ?: 0,
            carbsG = (m["carbsG"] as? Number)?.toInt() ?: 0,
            fatG = (m["fatG"] as? Number)?.toInt() ?: 0
        )
    }
    val t = get("totals") as? Map<*, *> ?: emptyMap<Any, Any>()
    val totals = MealTotals(
        calories = (t["calories"] as? Number)?.toInt() ?: 0,
        proteinG = (t["proteinG"] as? Number)?.toInt() ?: 0,
        carbsG = (t["carbsG"] as? Number)?.toInt() ?: 0,
        fatG = (t["fatG"] as? Number)?.toInt() ?: 0
    )
    return MealEntry(
        mealId = id,
        date = date,
        loggedAt = (get("loggedAt") as? Timestamp)?.toDate()?.toInstant() ?: Instant.now(),
        inputType = getString("inputType") ?: "text",
        description = getString("description") ?: "",
        items = items,
        totals = totals
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt
git commit -m "feat: add FirestoreMealRepository"
```

---

### Task 11: FirestoreWaterRepository + flip the service locator

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/data/water/FirestoreWaterRepository.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt`

- [ ] **Step 1: Create `FirestoreWaterRepository.kt`**

```kotlin
package com.myhealthtracker.app.data.water

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirestoreWaterRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : WaterRepository {

    private val _waterLog = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val waterLog: StateFlow<Map<String, Int>> = _waterLog.asStateFlow()

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) attachListener(uid) else _waterLog.value = emptyMap()
        }
        auth.currentUser?.uid?.let { attachListener(it) }
    }

    private fun attachListener(uid: String) {
        firestore.collection("users").document(uid).collection("water")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _waterLog.value = snap.documents.associate { doc ->
                    doc.id to ((doc.get("amountMl") as? Number)?.toInt() ?: 0)
                }
            }
    }

    override fun addWater(date: String, amountMl: Int) {
        val uid = auth.currentUser?.uid ?: return
        val doc = firestore.collection("users").document(uid).collection("water").document(date)
        doc.set(
            mapOf(
                "date" to date,
                "amountMl" to FieldValue.increment(amountMl.toLong()),
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        )
    }
}
```

- [ ] **Step 2: Update `AppContainer.kt` to use the Firestore repos**

```kotlin
package com.myhealthtracker.app.di

import com.myhealthtracker.app.data.meal.FirestoreMealRepository
import com.myhealthtracker.app.data.meal.FunctionsMealAnalyzer
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.FirestoreWaterRepository
import com.myhealthtracker.app.data.water.WaterRepository

object AppContainer {
    val mealRepository: MealRepository by lazy { FirestoreMealRepository() }
    val waterRepository: WaterRepository by lazy { FirestoreWaterRepository() }
    val mealAnalyzer: MealAnalyzer by lazy { FunctionsMealAnalyzer() }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/water/FirestoreWaterRepository.kt app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt
git commit -m "feat: add FirestoreWaterRepository and switch AppContainer to Firestore repos"
```

---

### Task 12: Emulator instrumented test for Firestore repos

**Files:**
- Create: `app/src/androidTest/java/com/myhealthtracker/app/FirestoreMealWaterEmulatorTest.kt`

This verifies water-increment idempotency and meal write+listen against the Firestore emulator. Requires the emulator running (see run step).

- [ ] **Step 1: Write the instrumented test**

```kotlin
package com.myhealthtracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.meal.FirestoreMealRepository
import com.myhealthtracker.app.data.water.FirestoreWaterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestoreMealWaterEmulatorTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() = runBlocking {
        firestore = FirebaseFirestore.getInstance().apply { useEmulator("10.0.2.2", 8080) }
        auth = FirebaseAuth.getInstance().apply { useEmulator("10.0.2.2", 9099) }
        auth.signInAnonymously().await()
    }

    @Test
    fun waterIncrementIsIdempotentPerDate() = runBlocking {
        val repo = FirestoreWaterRepository(firestore, auth)
        val date = "2026-06-13"
        repo.addWater(date, 250)
        repo.addWater(date, 250)
        val log = withTimeout(5000) {
            repo.waterLog.first { (it[date] ?: 0) >= 500 }
        }
        assertEquals(500, log[date])
    }

    @Test
    fun mealWriteThenListenReturnsEntry() = runBlocking {
        val repo = FirestoreMealRepository(firestore, auth)
        repo.addMeal(
            date = "2026-06-13", inputType = "text", description = "test",
            items = listOf(MealItem("Egg", "2", 140, 12, 1, 10)),
            totals = MealTotals(140, 12, 1, 10)
        )
        val meals = withTimeout(5000) { repo.meals.first { it.isNotEmpty() } }
        assertEquals("test", meals.first().description)
        assertEquals(140, meals.first().totals.calories)
    }
}
```

Add the import `import kotlinx.coroutines.tasks.await` at the top as well.

- [ ] **Step 2: Start the Firestore + Auth emulators**

Run: `firebase emulators:start --only firestore,auth`
Expected: emulators listening on 8080 (firestore) and 9099 (auth).

- [ ] **Step 3: Run the instrumented test (device/emulator required)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*FirestoreMealWaterEmulatorTest"`
Expected: PASS (2 tests).

> If no Android device/emulator is available in this environment, mark this task blocked and note it; the source still builds and is covered by review.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/myhealthtracker/app/FirestoreMealWaterEmulatorTest.kt
git commit -m "test: emulator tests for Firestore meal write + water increment"
```

---

### Task 13: Image downscale + base64 encoder

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt`

Pure-ish Android utility (uses `Bitmap`); covered by the meal flow and review rather than a unit test (Bitmap needs instrumentation). Keep it small and obvious.

- [ ] **Step 1: Create `ImageEncoder.kt`**

```kotlin
package com.myhealthtracker.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageEncoder {
    private const val MAX_DIM = 1024
    private const val JPEG_QUALITY = 80

    /** Loads the image at [uri], downscales it, and returns base64 JPEG. Returns null on failure. */
    fun uriToBase64Jpeg(context: Context, uri: Uri): String? {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        return bitmapToBase64Jpeg(original)
    }

    fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val scaled = downscale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val bytes = out.toByteArray()
        if (scaled != bitmap) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_DIM) return bitmap
        val ratio = MAX_DIM.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt
git commit -m "feat: add image downscale + base64 encoder util"
```

---

### Task 14: Rewrite AddMealViewModel against the real analyzer (TDD)

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt` (full rewrite)
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.meal.MealAnalysisException
import com.myhealthtracker.app.data.meal.MealAnalysisResult
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class AddMealViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAnalyzer(
        var result: MealAnalysisResult? = null,
        var error: String? = null
    ) : MealAnalyzer {
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            error?.let { throw MealAnalysisException(it) }
            return result!!
        }
    }

    private class FakeMealRepo : MealRepository {
        val saved = mutableListOf<MealEntry>()
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals) {
            saved.add(MealEntry("id", date, java.time.Instant.now(), inputType, description, items, totals))
        }
        override fun deleteMeal(mealId: String) {}
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful text analysis moves to result with items and lowConfidence`() = runTest(dispatcher) {
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Egg", "2", 140, 12, 1, 10)),
                totals = MealTotals(140, 12, 1, 10),
                lowConfidence = true
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer)
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.ResultState, vm.step.value)
        assertEquals(1, vm.recognizedItems.value.size)
        assertTrue(vm.lowConfidence.value)
    }

    @Test
    fun `empty text is rejected before analysis`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer())
        vm.onDescriptionChange("   ")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertTrue(vm.errorMessage.value != null)
    }

    @Test
    fun `analysis error returns to input with friendly message`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer(error = "boom"))
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals("boom", vm.errorMessage.value)
    }

    @Test
    fun `saving recognized items writes to repository`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(listOf(MealItem("Egg", "2", 140, 12, 1, 10)), MealTotals(140, 12, 1, 10), false)
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals(1, repo.saved.size)
        assertEquals(140, repo.saved.first().totals.calories)
        assertTrue(vm.isSaved.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AddMealViewModelTest"`
Expected: FAIL — `AddMealViewModel` constructor signature and `analyzeText`/`lowConfidence` don't exist yet.

- [ ] **Step 3: Rewrite `AddMealViewModel.kt`**

```kotlin
package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.meal.MealAnalysisException
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object Loading : AddMealStep()
    object ResultState : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analyzer: MealAnalyzer = AppContainer.mealAnalyzer
) : ViewModel() {

    private val _step = MutableStateFlow<AddMealStep>(AddMealStep.InputSelection)
    val step: StateFlow<AddMealStep> = _step.asStateFlow()

    private val _mealDescription = MutableStateFlow("")
    val mealDescription: StateFlow<String> = _mealDescription.asStateFlow()

    private val _recognizedItems = MutableStateFlow<List<MealItem>>(emptyList())
    val recognizedItems: StateFlow<List<MealItem>> = _recognizedItems.asStateFlow()

    private val _lowConfidence = MutableStateFlow(false)
    val lowConfidence: StateFlow<Boolean> = _lowConfidence.asStateFlow()

    private val _manualCal = MutableStateFlow("")
    val manualCal: StateFlow<String> = _manualCal.asStateFlow()
    private val _manualProtein = MutableStateFlow("")
    val manualProtein: StateFlow<String> = _manualProtein.asStateFlow()
    private val _manualCarbs = MutableStateFlow("")
    val manualCarbs: StateFlow<String> = _manualCarbs.asStateFlow()
    private val _manualFat = MutableStateFlow("")
    val manualFat: StateFlow<String> = _manualFat.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    // "text" or "image" of the last successful analysis, used when saving.
    private var lastInputType: String = "text"

    fun onDescriptionChange(desc: String) {
        _mealDescription.value = desc
        _errorMessage.value = null
    }

    fun onManualCalChange(v: String) { _manualCal.value = v }
    fun onManualProteinChange(v: String) { _manualProtein.value = v }
    fun onManualCarbsChange(v: String) { _manualCarbs.value = v }
    fun onManualFatChange(v: String) { _manualFat.value = v }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun analyzeText() {
        val desc = _mealDescription.value.trim()
        if (desc.isEmpty()) {
            _errorMessage.value = "אנא הקלד תיאור של הארוחה"
            return
        }
        runAnalysis(inputType = "text", text = desc, imageBase64 = null)
    }

    fun analyzeImage(imageBase64: String) {
        runAnalysis(inputType = "image", text = null, imageBase64 = imageBase64)
    }

    private fun runAnalysis(inputType: String, text: String?, imageBase64: String?) {
        viewModelScope.launch {
            _errorMessage.value = null
            _step.value = AddMealStep.Loading
            try {
                val result = analyzer.analyze(inputType, text, imageBase64, today())
                lastInputType = inputType
                _recognizedItems.value = result.items
                _lowConfidence.value = result.lowConfidence
                _step.value = AddMealStep.ResultState
            } catch (e: MealAnalysisException) {
                _errorMessage.value = e.message
                _step.value = AddMealStep.InputSelection
            }
        }
    }

    fun updateItem(index: Int, updatedItem: MealItem) {
        val list = _recognizedItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = updatedItem
            _recognizedItems.value = list
        }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val manual = _step.value == AddMealStep.ManualFallback
            val description = if (manual) {
                _mealDescription.value.ifEmpty { "ארוחה ידנית" }
            } else {
                _mealDescription.value.ifEmpty { "ארוחה מנותחת AI" }
            }

            val items: List<MealItem>
            val totals: MealTotals
            if (manual) {
                val cal = _manualCal.value.toIntOrNull() ?: 0
                if (cal <= 0) {
                    _errorMessage.value = "הקלוריות חייבות להיות גדולות מ-0"
                    return@launch
                }
                val protein = _manualProtein.value.toIntOrNull() ?: 0
                val carbs = _manualCarbs.value.toIntOrNull() ?: 0
                val fat = _manualFat.value.toIntOrNull() ?: 0
                items = listOf(MealItem(description, "1 מנה", cal, protein, carbs, fat))
                totals = MealTotals(cal, protein, carbs, fat)
            } else {
                if (_recognizedItems.value.isEmpty()) {
                    _errorMessage.value = "אין פריטים לשמירה"
                    return@launch
                }
                items = _recognizedItems.value
                totals = MealTotals(
                    calories = items.sumOf { it.calories },
                    proteinG = items.sumOf { it.proteinG },
                    carbsG = items.sumOf { it.carbsG },
                    fatG = items.sumOf { it.fatG }
                )
            }

            mealRepository.addMeal(
                date = today(),
                inputType = if (manual) "text" else lastInputType,
                description = description,
                items = items,
                totals = totals
            )
            _isSaved.value = true
        }
    }

    fun switchToManualFallback() {
        _errorMessage.value = null
        _step.value = AddMealStep.ManualFallback
    }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AddMealViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
git commit -m "feat: AddMealViewModel uses real analyzer + Firestore save (TDD)"
```

---

### Task 15: Wire real image capture/pick + lowConfidence banner in AddMealScreen

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Create `app/src/main/res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="meal_images" path="meal_images/" />
</paths>
```

- [ ] **Step 2: Register the FileProvider in `AndroidManifest.xml`** (inside `<application>`, after the activity)

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Replace the `InputSelectionContent` callers in `AddMealScreen` with real launchers**

In the `@Composable fun AddMealScreen(...)`, add `lowConfidence` collection and the launchers, then update the `InputSelection` branch. Replace lines that currently read:

```kotlin
    val isSaved by viewModel.isSaved.collectAsState()
```

with:

```kotlin
    val isSaved by viewModel.isSaved.collectAsState()
    val lowConfidence by viewModel.lowConfidence.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val base64 = com.myhealthtracker.app.util.ImageEncoder.uriToBase64Jpeg(context, uri)
            if (base64 != null) viewModel.analyzeImage(base64)
        }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val base64 = com.myhealthtracker.app.util.ImageEncoder.uriToBase64Jpeg(context, uri)
            if (base64 != null) viewModel.analyzeImage(base64)
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
```

- [ ] **Step 4: Add the camera helper at the bottom of `AddMealScreen.kt`**

```kotlin
private fun createCameraImageUri(context: android.content.Context): android.net.Uri {
    val dir = java.io.File(context.cacheDir, "meal_images").apply { mkdirs() }
    val file = java.io.File.createTempFile("meal_", ".jpg", dir)
    return androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
}
```

- [ ] **Step 5: Update the `AddMealStep.InputSelection` branch to use launchers + text analysis**

```kotlin
            AddMealStep.InputSelection -> {
                InputSelectionContent(
                    mealDescription = mealDescription,
                    errorMessage = errorMessage,
                    onDescriptionChange = { viewModel.onDescriptionChange(it) },
                    onAnalyzeTextClick = { viewModel.analyzeText() },
                    onPickImageClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onCameraClick = {
                        val uri = createCameraImageUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    onManualClick = { viewModel.switchToManualFallback() },
                    modifier = contentModifier
                )
            }
```

- [ ] **Step 6: Update `InputSelectionContent` signature + image area** to expose both camera and gallery (replace the single `onImageClick`)

Change the function signature:

```kotlin
@Composable
private fun InputSelectionContent(
    mealDescription: String,
    errorMessage: String?,
    onDescriptionChange: (String) -> Unit,
    onAnalyzeTextClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
```

In the image section, replace the single clickable `Box`'s `.clickable { onImageClick() }` with two buttons under the prompt text:

```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCameraClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("📷 צילום") }
                OutlinedButton(
                    onClick = onPickImageClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("🖼️ גלריה") }
            }
```

- [ ] **Step 7: Update the preview `AddMealScreenPreviewInput`** to match the new signature

```kotlin
        InputSelectionContent(
            mealDescription = "סלט חזה עוף מפנק",
            errorMessage = null,
            onDescriptionChange = {},
            onAnalyzeTextClick = {},
            onPickImageClick = {},
            onCameraClick = {},
            onManualClick = {}
        )
```

- [ ] **Step 8: Add a lowConfidence banner in `ResultStateContent`**

Pass `lowConfidence` into `ResultStateContent` (add parameter `lowConfidence: Boolean`) from the `ResultState` branch, and render at the top of its `Column`:

```kotlin
        if (lowConfidence) {
            Text(
                text = "⚠️ הזיהוי אינו ודאי — מומלץ לעבור על הכמויות ולערוך לפני שמירה.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
```

In the `ResultState` branch of `AddMealScreen`, pass `lowConfidence = lowConfidence`. In the `ResultStateContent` preview, pass `lowConfidence = false`.

- [ ] **Step 9: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "feat: wire camera/gallery capture + lowConfidence banner in AddMealScreen"
```

---

### Task 16: Point FoodViewModel at the service locator

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt:30-33`

The water unit stays ml; only the repo defaults change so the live screen reads/writes Firestore.

- [ ] **Step 1: Update the constructor defaults**

Replace:

```kotlin
class FoodViewModel(
    private val mealRepository: MealRepository = FakeRepository,
    private val waterRepository: WaterRepository = FakeRepository
) : ViewModel() {
```

with:

```kotlin
class FoodViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val waterRepository: WaterRepository = AppContainer.waterRepository
) : ViewModel() {
```

and update imports: remove `import com.myhealthtracker.app.data.FakeRepository`, add `import com.myhealthtracker.app.di.AppContainer`.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full unit test suite (regression)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (existing tests + new meal tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt
git commit -m "feat: FoodViewModel reads meals/water from Firestore via AppContainer"
```

---

### Task 17: Docs + final verification

**Files:**
- Modify: `CLAUDE.md` (water schema)
- Modify: `docs/CHANGELOG.md`

- [ ] **Step 1: Update the water schema in `CLAUDE.md`**

Replace the line:

```
├── water/{date}      : { date, cups, updatedAt }
```

with:

```
├── water/{date}      : { date, amountMl, updatedAt }   (כמות מצטברת במ"ל, idempotent — הגדלה ב-FieldValue.increment)
```

- [ ] **Step 2: Add a CHANGELOG entry** at the top of `docs/CHANGELOG.md`

```markdown
## Phase 2 — Meal Logging + AI Analysis

- Added `analyzeMeal` Cloud Function (TypeScript, 2nd gen): App Check + Auth gated, Vertex AI
  (Gemini) analysis of meal text/photo, structured JSON output, server-side totals, timeout +
  retry, uniform error envelope. **Analysis-only** — does not persist (diverges from the original
  HLD contract, which had the function write the meal; the client now writes after the user edits).
- Android: real `analyzeMeal` callable wiring (`MealAnalyzer`), camera/gallery capture with
  downscale→base64 (images never stored), edit-before-save, Firestore-backed meal + water repos
  (water kept in ml), App Check, and a lightweight `AppContainer` service locator.
- Added `firestore.rules` (per-user access) and the `functions/` project with Jest tests.
```

- [ ] **Step 3: Run the full backend test suite**

Run: `npm --prefix functions test`
Expected: PASS (validation, prompt, parse, vertexClient).

- [ ] **Step 4: Build the backend**

Run: `npm --prefix functions run build`
Expected: `tsc` clean.

- [ ] **Step 5: Run the full Android unit suite + assemble debug**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md docs/CHANGELOG.md
git commit -m "docs: update water schema (ml) and add Phase 2 changelog"
```

- [ ] **Step 7: STOP and hand off the manual steps to the user**

Report `[✓ הושלם]` for code/tests, and list the remaining **manual** operations for the user:
- `firebase deploy --only functions`
- Enable the Vertex AI API in GCP.
- Configure App Check (register app, Play Integrity, add debug token).
- Confirm `.firebaserc` project id + Vertex service-account permissions.
- (Optional) run the emulator instrumented test (Task 12) on a device.

---

## Self-Review

**Spec coverage:**
- analyzeMeal callable (App Check, Auth, validation, profile, Vertex, structured JSON, recomputed totals, retry/timeout, error envelope, logging, no-food→empty) → Tasks 2–6. ✓
- Client analyzer + edit-before-save + Firestore save → Tasks 9, 14. ✓
- Camera/gallery, downscale→base64, image never stored → Tasks 13, 15. ✓
- Firestore meal + water (ml) repos, idempotent water increment → Tasks 10–12. ✓
- App Check on client → Task 8. ✓
- Service locator → Tasks 8, 11, 16. ✓
- Security rules → Task 1. ✓
- Tests (server Jest; client unit; emulator instrumented) → Tasks 2–5, 9, 12, 14. ✓
- Manual-step stops → after Task 6 and Task 17. ✓
- Doc updates (CLAUDE.md water, CHANGELOG, HLD divergence note) → Task 17. ✓

**Placeholder scan:** No TBD/TODO; every code step includes complete code. ✓

**Type consistency:** `MealAnalysisResult(items, totals, lowConfidence)`, `MealAnalyzer.analyze(inputType, text, imageBase64, date)`, `AppContainer.{mealRepository, waterRepository, mealAnalyzer}`, and `AddMealViewModel(mealRepository, analyzer)` are used consistently across Tasks 9, 11, 14, 15. `MealRepository`/`WaterRepository` interface signatures match `FakeRepository`. ✓
