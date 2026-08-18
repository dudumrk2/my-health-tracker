# Design: Resilient Meal Analysis Flow (background + auto-save)

> Date: 2026-06-25
> Status: Approved (pending spec review)
> Phase: Phase 2 (meal logging + analyzeMeal). Does not touch the daily insights/summary pipeline.

## Problem

Today meal analysis runs inside `AddMealViewModel.runAnalysis` on `viewModelScope`. This means:

- The analysis dies if the user leaves the `AddMeal` screen (the work is tied to the screen lifecycle).
- A failed `analyzeMeal` call (`MealAnalysisException`) dumps the user back to the input step with **no saved meal** and only a single, non-retried attempt.
- The captured photo is encoded to base64, sent, and thrown away — it is never available again, so a saved meal can never show its photo.

The goal is a flow that is durable across screen/app lifecycle, retries on failure, persists the photo locally, lets the user leave and be notified, and never leaves the user unable to save.

## Decisions (from brainstorming)

1. **Save model: always background + auto-save.** The moment the user sends a meal for analysis, the meal is persisted immediately as `analyzing`. Analysis runs in the background. On completion the meal is updated in place. Editing is available *after* save (no pre-save curation gate).
2. **In-progress / failed meals appear in the journal** as cards with a status badge. Their calories/macros do **not** count toward daily totals or insight gating until `complete`.
3. **Proactive failure recovery** is shown as a **top banner on the Food screen** ("יש N מנות שלא נותחו"), in addition to the failed card itself.
4. **Unseen-result interception + celebration:** a completed meal the user has not yet seen pops its result screen on the next Dashboard entry (cold start, app reopen, or the analysis-complete notification), and celebrates if quality warrants. **Exception:** if the app was launched via the quick-action notification buttons (`DEST_ADD_MEAL` / `DEST_ADD_WORKOUT`), skip the interception and open the requested add screen.
5. **Photos are stored locally on-device only**, never uploaded to the cloud. This refines iron-rule #2 in `CLAUDE.md` (see "Iron-rule update").
6. **Text meals use the same background pipeline** (same slowness/failure exposure), simply without a local image.

## Architecture overview

Replace the screen-scoped analysis with a durable pipeline:

```
[AddMeal: send]
   → copy image to filesDir/meal_images/{mealId}.jpg  (image only)
   → MealRepository.createPendingMeal(...)  → Firestore doc {status:"analyzing", seen:false, localImagePath, note}
   → MealAnalysisScheduler.enqueue(mealId)  (WorkManager, unique per mealId)
   → close AddMeal screen immediately (meal already visible in the journal as "analyzing")

[MealAnalysisWorker]  (background, retrying)
   → read image from disk → ImageEncoder.base64 → mealAnalyzer.analyze(...)
   → success: MealRepository.completeMeal(mealId, items, totals, quality, recommendation)
   → terminal failure (attempts exhausted): MealRepository.failMeal(mealId, reason)
   → if app in background at completion: post meal_analysis notification (deep-link to the meal)

[Firestore snapshot listener]  (already exists)
   → streams status/items updates to FoodScreen, Dashboard, unseen-interceptor
```

Rationale for status-on-document (vs a separate local Room queue): reuses the existing Firestore offline cache and snapshot listener, makes in-progress/failed meals appear in the journal naturally, and gives free cross-surface sync. The **image bytes never leave the device** — only the local path string is stored in the doc.

---

## Section 1 — Data model & persistence

### `MealEntry` (and the Firestore `meals/{mealId}` doc) — new fields

- `status: String` — `"analyzing" | "complete" | "failed"`. **Default `"complete"`** when the field is absent, so all existing/manual meals are treated as complete (backward compatible).
- `localImagePath: String?` — absolute path to the on-device private file (`filesDir/meal_images/{mealId}.jpg`). `null` for text meals or when the file is gone.
- `note: String?` — the free-text note entered alongside the photo (already sent to AI). Persisted so a failed meal can be re-sent without re-typing.
- `failureReason: String?` — short user-facing reason for the failure banner/screen.
- `seen: Boolean` — default `false` on creation; `true` for manual meals. Set to `true` once the user has viewed the completed result (see Section 3, unseen interception).

`MealQuality`/`MealItem`/`MealTotals` are unchanged.

### `MealRepository` — new/changed methods

- `createPendingMeal(date, inputType, description, note, localImagePath, text): String`
  Creates a doc with `status="analyzing"`, `seen=false`, empty `items`/`totals`, and returns the generated `mealId`. Works offline against the Firestore cache (the id is generated client-side via `collection.document()`).
- `completeMeal(mealId, items, totals, quality, recommendation)` — sets `status="complete"` and the analysis fields. Clears `failureReason`.
- `failMeal(mealId, reason)` — sets `status="failed"`, `failureReason=reason`.
- `markMealSeen(mealId)` — sets `seen=true`.
- `updateMeal(mealId, description, items, totals)` — edits a saved meal's contents (new capability; recomputes totals from items). Keeps existing `status`/`quality`/`recommendation`.
- `deleteMeal(mealId)` — unchanged signature, but also deletes the local image file (Section 4).

`toMealEntry()` mapping reads the new fields with the defaults above.

### Totals & insight gating

`FoodViewModel` sums calories/macros only over meals with `status == "complete"`. The meal-gating used for insights counts only `complete` meals. `analyzing`/`failed` meals are listed but contribute 0.

---

## Section 2 — Background pipeline (WorkManager)

### `MealAnalysisWorker(CoroutineWorker)`

- **Input data:** `mealId`, `inputType` (`text`/`image`), `localImagePath?`, `text?`, `note?`, `date`.
- **Work:**
  1. If `image`: read the file from `localImagePath`, encode via the existing `ImageEncoder` (off the main thread). If the file is missing → terminal `failMeal("התמונה אינה זמינה")`, `Result.success()`.
  2. Call `AppContainer.mealAnalyzer.analyze(inputType, text, imageBase64, date)`.
  3. **Success** → `completeMeal(...)`; post the completion notification if the app is in the background; `Result.success()`.
  4. **Failure (transient)** → if `runAttemptCount < MAX_ATTEMPTS (4)` return `Result.retry()`. WorkManager applies exponential backoff (base 10s).
  5. **Failure (attempts exhausted)** → `failMeal(reason)`, post a failure notification if backgrounded, `Result.success()` (never `retry`, to avoid an infinite loop).
- **Constraints:** `NetworkType.CONNECTED`.

`MealAnalysisException` messages map to friendly Hebrew reasons (reuse `FunctionsMealAnalyzer`'s existing mapping for the stored `failureReason`).

### `MealAnalysisScheduler`

- `enqueue(context, mealId, inputData)` → `enqueueUniqueWork("mealAnalysis_$mealId", ExistingWorkPolicy.REPLACE, request)`. Mirrors `HealthSyncScheduler`. The unique-per-mealId name lets a manual "נסה שוב" re-enqueue the same meal cleanly.
- "Try again" from the failure UI: set the doc back to `status="analyzing"` and re-enqueue the same `mealId`.

### Text meals

Text analysis flows through the same `createPendingMeal` → worker path, with `localImagePath=null` and `text` set. The manual-entry fallback (`ManualFallback`) still saves directly as `complete` (no AI, no worker).

---

## Section 3 — UI

### `AddMealScreen` / `AddMealViewModel`

- On "שלח לניתוח" (text or image): the VM calls `createPendingMeal`, enqueues the worker, then signals the screen to dismiss immediately. The blocking `Loading` step is removed for the AI path.
- `ImagePreview` step stays (choose photo + optional note), but "שלח" now persists + enqueues instead of awaiting the result.
- `ManualFallback` step is unchanged (direct save as `complete`).
- Image is copied to `filesDir/meal_images/{mealId}.jpg` before/at `createPendingMeal`.

### Food journal cards (`FoodScreen`)

- Status badge per card: `analyzing` → "⏳ מנתח…"; `failed` → "⚠️ נכשל — הקש לתיקון"; `complete` → as today.
- Image meals show a thumbnail from `localImagePath` (Coil `AsyncImage`; gracefully omitted if the file is missing).
- Tapping a `complete` meal opens the detail sheet; tapping a `failed` meal opens the failure-recovery view (try again / manual entry / delete).

### Failure banner

- When the selected day (or, for proactivity, any recent meal) has `failed` meals, show a dismissible-on-fix top banner on the Food screen: "יש N מנות שלא נותחו" → tapping opens the relevant meal for recovery.

### Meal detail + edit (`MealDetailSheet` → editor)

- The detail sheet shows the photo (if any) and gains an **"ערוך"** button.
- Editing reuses the existing item editor (`ResultStateContent` extracted/reused), seeded from the `MealEntry`. Saving calls `updateMeal(mealId, …)`.
- A new nav key `EditMeal(mealId)` (or a result/edit screen) renders this for an existing meal; used by both manual edit and the unseen-interception/notification deep link.

### Completion notification

- New channel `meal_analysis` (`IMPORTANCE_DEFAULT`). Posted by the worker **only when the app is in the background**:
  - success: "המנה נותחה ✓ {calories} קלוריות" → deep-links to the meal (opens its result screen).
  - failure: "לא הצלחנו לנתח מנה" → deep-links to the meal's recovery view.
- Reuses the `EXTRA_NAVIGATE_TO` mechanism with a new destination carrying the `mealId` (e.g. `DEST_MEAL_RESULT` + `EXTRA_MEAL_ID`), routed in `MainNavigation`.

### Unseen-result interception + celebration

- A single observer in `MainNavigation` (or MainActivity-level) watches the meals flow. When the app lands on **Dashboard** and the launching intent is **not** a quick-action add (`DEST_ADD_MEAL`/`DEST_ADD_WORKOUT`):
  - If there are `complete` meals with `seen == false`, navigate to the result screen (`EditMeal(mealId)`) for the **most recent** unseen meal, run `CelebrationRules.mealQuality(quality, date)` via `celebrationController.celebrateNow(...)` if it qualifies, and mark **all** currently-unseen completed meals `seen=true` (avoids repeated nagging).
- This fires for cold start, app reopen, foreground completion, and the analysis-complete notification (which targets the specific meal directly).
- **Quick-action exception:** when launched via `DEST_ADD_MEAL`/`DEST_ADD_WORKOUT`, skip the interception entirely and open the requested add screen; unseen meals remain accessible from the journal and pop on a later normal entry.

### Celebrations

- High-quality meal celebration moves from "on save" to "when the completed result is presented to the user" (foreground completion or unseen interception). Background completions that the user never opens are surfaced via the notification, not an overlay.

---

## Section 4 — Image storage, retention, and the iron rule

- **Storage:** `filesDir/meal_images/{mealId}.jpg` (app-private internal storage; not `cacheDir`, so it is not auto-evicted). The selected/captured image is downscaled (existing `ImageEncoder` parameters) and copied here at `createPendingMeal`.
- **Retention:** the image persists for `complete` and `failed` meals so the user can view it or retry. It is deleted when the meal is deleted. On app start, an orphan sweep removes `meal_images` files with no matching meal id.
- **Iron-rule update (`CLAUDE.md` #2):** reword from "תמונות אוכל לא נשמרות" to: *"תמונות אוכל לעולם לא עולות/נשמרות בענן; ניתן לשמור אותן מקומית במכשיר בלבד (לצפייה חוזרת ולניסיון ניתוח חוזר). הניתוח נשלח לפונקציה, התמונה לא עוזבת את המכשיר."* Update `HLD`/`CHANGELOG` accordingly.

---

## Section 5 — Testing

- **Unit:**
  - `MealAnalysisWorker`: success → `completeMeal`; transient failure under max attempts → `Result.retry()`; attempts exhausted → `failMeal` + `Result.success()`; missing image file → terminal fail. Uses a fake `MealAnalyzer` and a fake `MealRepository`.
  - `FoodViewModel`: totals/macros sum only `complete` meals; failed-meal count drives the banner; unseen-completed detection.
  - `MealRepository` mapping: `status`/`localImagePath`/`note`/`seen`/`failureReason` round-trip with backward-compatible defaults; `updateMeal` recomputes totals.
  - Unseen-interception decision logic (pure function): given meals + launch source → route target (meal result / add screen / none).
- **Existing tests** `AddMealViewModelTest` and `FunctionsMealAnalyzerTest` are updated to the new create-pending/enqueue flow.
- Build + relevant unit suite must pass before completion (`./gradlew test`, `assembleDebug`).

## Out of scope

- The daily insights/summary pipeline (Phase 3) is untouched, beyond gating on `complete` meals where it already reads meals.
- No server/Cloud Function changes — `analyzeMeal` is unchanged.
- No multi-device image sync (images are device-local by design).

## Backward compatibility

- Existing meal docs lack `status`/`seen` → read as `complete`/`seen=true`, so they render and count exactly as today.
- Manual meals are created `complete`/`seen=true` and never enqueue a worker.
