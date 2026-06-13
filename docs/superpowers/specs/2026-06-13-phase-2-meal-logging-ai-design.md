# Phase 2 Design — Meal Logging + AI Analysis

> Spec for Phase 2 of MyHealthTracker. Source of truth: `CLAUDE.md`, `docs/HLD-health-tracker.md`,
> `prompts/prompt-phase-2.md`. This document records the design and decisions agreed during brainstorming.
> Date: 2026-06-13.

## Goal

Implement meal logging with AI analysis: the user describes a meal in free text or takes/picks a
photo, the input is analyzed by Gemini (via Vertex AI, server-side only), the structured result
(items + nutritional totals) is shown for **manual editing**, and the final result is saved to
Firestore. Includes a quick "+ water" action. No daily insights (that is Phase 3).

## Locked decisions

1. **Functions runtime:** TypeScript, Cloud Functions 2nd gen.
2. **Water units:** keep **ml** (not `cups`). `CLAUDE.md` water schema updated `cups` → `amountMl`.
3. **Repository wiring:** lightweight service locator (`AppContainer`) returning Firestore impls in-app,
   `FakeRepository` in previews/tests.
4. **Persistence model:** `analyzeMeal` is **analysis-only** — it never writes Firestore and never
   stores the image. The client persists the meal **after the user edits**. This diverges from the
   HLD `analyzeMeal` contract (which had the function write the meal + return `mealId`); the change is
   required by prompt-phase-2's edit-before-save requirement.
5. **Gemini model:** `gemini-2.5-flash` via Vertex AI (env-configurable).

## Current state (what already exists)

- UI mock screens for meals/food/water are built (`AddMealScreen`, `AddMealViewModel`, `FoodScreen`,
  `FoodViewModel`) and currently use the in-memory `FakeRepository` with mocked `delay()` analysis.
- Image input in `AddMealScreen` is faked (sets a description string).
- `MealRepository` / `WaterRepository` are interfaces implemented **only** by `FakeRepository`.
- `ProfileRepository` + `FirestoreProfileRepository` is the established pattern for real Firestore wiring.
- Android Firebase deps: Auth + Firestore only. **No** Functions client SDK, **no** App Check SDK.
- **No** `functions/` directory and **no** `firebase.json` exist yet.

## Architecture

```
Android (AddMealViewModel) ──callable: analyzeMeal──▶ Cloud Function (TS, 2nd gen)
       │                                                    │
       │  user edits result                                 ├─ App Check + Auth gate
       ▼                                                    ├─ read users/{uid}/profile (server-side)
  Firestore write ◀─────────────────────────────────────── ┤─ Vertex AI (Gemini, vision/text)
  users/{uid}/meals/{mealId}                                └─ return { items[], totals, lowConfidence }
```

The image exists only in the in-memory request; it is never persisted anywhere.

## Component 1 — Cloud Functions backend (`functions/`)

New TypeScript project at repo root with `firebase.json`, `.firebaserc`, `functions/package.json`,
`functions/tsconfig.json`, `functions/src/index.ts`, and supporting modules.

### `analyzeMeal` (HTTPS Callable, 2nd gen)

- **Gate:** `enforceAppCheck: true`; reject calls without a valid App Check token. Require
  `request.auth` else throw `unauthenticated`.
- **Request:** `{ inputType: "text" | "image", text?: string, imageBase64?: string, date: "yyyy-MM-dd" }`.
- **Validation:** `inputType` ∈ {text, image}; for `text` require non-empty `text`; for `image` require
  non-empty `imageBase64`; `date` matches `yyyy-MM-dd`. Failure → `invalid-argument`.
- **Profile context:** read `users/{uid}/profile` server-side (weight/height/gender) to inform portion
  estimation per Contract A. Best-effort: proceed without if missing. Never trusted from the client.
- **Gemini call (Vertex AI):** `@google-cloud/vertexai`, model `gemini-2.5-flash`. Use structured
  output (`responseMimeType: "application/json"` + `responseSchema`) to force the `items[]` schema with
  no wrapping text/Markdown. Image → `inlineData` (base64); text → text part. Prompt per Contract A:
  estimate when unsure, metric units, no diagnosis/diet advice, **empty/foodless image → `items: []`**,
  set `lowConfidence: true` when uncertain.
- **Output:** parse + validate JSON shape; **recompute `totals` from `items` server-side** (don't trust
  model-provided sums); pass through `lowConfidence`. Return `{ items[], totals, lowConfidence }`. No
  `mealId` (client owns persistence).
- **Resilience:** Vertex call timeout 15s; retry max 2 on 429/503/timeout with exponential backoff
  (not on input errors); overall function timeout 60s.
- **Failure → friendly error:** bad/non-JSON model output or Vertex failure → `internal` with a safe,
  display-friendly message so the client can fall back to manual entry.
- **Error envelope:** only `HttpsError` codes — `invalid-argument`, `unauthenticated`,
  `resource-exhausted` (Gemini quota / 429), `internal`. Never return the raw Gemini error to the client.
- **Logging:** structured logs with `requestId`, `uid`, `durationMs`, `model`. **Never log**
  `imageBase64`, image content, or keys.

### Item / totals schema (matches HLD)

```
item:   { name: string, quantity: string, calories: number, proteinG: number, carbsG: number, fatG: number }
totals: { calories, proteinG, carbsG, fatG }   // recomputed from items
```

## Component 2 — Android client

### Dependencies

Add to `gradle/libs.versions.toml` + `app/build.gradle.kts`:
- `firebase-functions-ktx`
- `firebase-appcheck-playintegrity`
- `firebase-appcheck-debug` (debug build only)

### App Check

Initialize at app startup: Play Integrity provider (release), Debug provider (debug builds).

### `MealAnalysisService`

Wraps the `analyzeMeal` callable. `suspend` API that builds the request `{ inputType, text?,
imageBase64?, date }`, awaits the callable, maps the response → `MealAnalysisResult(items, totals,
lowConfidence)`, and maps failures (`FirebaseFunctionsException` codes) → typed errors the ViewModel
can present (and decide to offer manual fallback).

### Image capture / pick

Use Activity Result APIs (no third-party deps):
- `ActivityResultContracts.TakePicture()` writing a full-res photo to a **temp cache file** via a
  `FileProvider` (manifest entry + `res/xml/file_paths.xml`).
- `ActivityResultContracts.PickVisualMedia()` for gallery selection (no permission required).
- Downscale to ~1024px max dimension, JPEG quality ~80, encode to base64.
- **Delete the temp file** immediately after encoding. The image is never persisted.
- Do **not** declare the `CAMERA` permission (we launch the external camera app) — honors the
  minimum-permissions iron rule.

### Repositories (Firestore-backed)

- `FirestoreMealRepository` implements `MealRepository`: `meals: StateFlow<List<MealEntry>>` backed by a
  snapshot listener on `users/{uid}/meals`; `addMeal(...)` writes a new `meals/{mealId}` doc;
  `deleteMeal(...)` deletes. `uid` from `FirebaseAuth.currentUser`.
- `FirestoreWaterRepository` implements `WaterRepository`: `waterLog: StateFlow<Map<String, Int>>` (ml)
  backed by a snapshot listener on `users/{uid}/water`; `addWater(date, amountMl)` performs an
  idempotent `FieldValue.increment(amountMl)` on `users/{uid}/water/{date}.amountMl` with `updatedAt`.
- Both keep the existing interface signatures so `FakeRepository` (previews/tests) stays valid.

### Service locator

`AppContainer` (or `RepositoryProvider`) object exposing `mealRepository`, `waterRepository`,
`profileRepository`, etc. Returns Firestore impls in-app, `FakeRepository` in previews/tests.
ViewModels resolve repos from it, replacing the current `= FakeRepository` constructor defaults.

### `AddMealViewModel` rewrite

- Replace mock `analyzeMeal()` (`delay()` + canned items) with a real `MealAnalysisService` call.
- Hold optional image bytes/base64 for the image path.
- Keep the four steps: `InputSelection`, `Loading`, `ResultState`, `ManualFallback`.
- Surface `lowConfidence` as a banner in `ResultState`.
- On analysis error: show a friendly message and offer the manual-fallback path.
- `saveMeal()` writes via `mealRepository.addMeal(...)` (Firestore) after edits.

### `AddMealScreen` wiring

- Wire real camera/gallery launchers; show an in-memory thumbnail of the selected image.
- Show the `lowConfidence` banner when set.

### `FoodViewModel`

- Rewired to Firestore repos via the service locator. Water stays ml (`quickAddWater(amountMl)`).
- Its local heuristic `aiAdvice` / `refreshAdvice` is **out of Phase 2 scope** (real AI insights are
  Phase 3); left as placeholder copy, untouched.

## Component 3 — Firestore Security Rules

Add `firestore.rules` (wired via `firebase.json`): `users/{uid}` and all subcollections (`meals`,
`water`, `profile`, `healthDaily`, `bodyMeasurements`) are readable/writable only when
`request.auth != null && request.auth.uid == uid`.

## Testing

### Server (Jest, Vertex client mocked)

- Valid JSON model output → correct `items[]` + recomputed `totals`.
- Invalid / non-JSON model output → `internal` friendly error (no raw Gemini text).
- Mocked Gemini/Vertex failure → `internal` friendly error.
- "No food in image" → `items: []`.
- Missing `text` (text mode) / missing `imageBase64` (image mode) → `invalid-argument`.
- Error envelope assertion: client-facing message never contains the raw Gemini error.

### Client

- **Unit (MockK):**
  - `MealAnalysisService` response mapping and error-code → typed-error mapping.
  - `AddMealViewModel` flow: input → loading → result → edit → save; error → manual fallback;
    empty-input validation; `lowConfidence` surfaced.
  - Request-building / base64 encoding logic where isolatable.
- **Instrumented (`connectedAndroidTest`, Firebase Local Emulator Suite):**
  - `FirestoreWaterRepository` increment idempotency.
  - `FirestoreMealRepository` write + snapshot-listener mapping.

## Manual steps (STOP and hand to the user)

These are the user's manual operations; pause and request them at the right point:
- `firebase deploy --only functions`
- Enable the Vertex AI API in the GCP project.
- Configure Firebase App Check (register the app, enable Play Integrity, add a debug token).
- Confirm `.firebaserc` project id and Vertex service-account permissions.

## Out of scope (do not touch)

- Daily AI insights / Cloud Scheduler / Pub/Sub (Phase 3).
- Storing images anywhere.
- Any Gemini/Vertex access on the client.

## Doc updates required

- `CLAUDE.md`: water schema `cups` → `amountMl` (ml).
- Note the `analyzeMeal` analysis-only divergence from the HLD contract.
- `docs/CHANGELOG.md` entry for Phase 2.
