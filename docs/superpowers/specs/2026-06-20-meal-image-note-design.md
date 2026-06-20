# Design Spec — Optional Note on Meal Photo Before AI Analysis

**Date:** 2026-06-20
**Phase:** 2 (meal logging + analyzeMeal)
**Status:** Approved (pending written-spec review)

## Goal

Let the user add a few optional words about a meal photo **before** it is sent to
the AI. Today, picking or capturing an image immediately sends it to `analyzeMeal`
with a fixed prompt and no chance to add context. The note gives the AI extra
context (ingredients, portion size, preparation) to improve analysis accuracy.

## Background — current flow

- `AddMealScreen.kt`: gallery/camera launchers call `viewModel.analyzeImageUri(...)`
  immediately on selection — straight to AI, no intermediate step.
- `AddMealViewModel.analyzeImageUri`: encodes the `Uri` to base64 on `Dispatchers.IO`,
  then calls `analyzer.analyze("image", null, base64, today())` (text is always `null`).
- `MealAnalyzer.analyze(inputType, text, imageBase64, date)`: interface **already**
  accepts an optional `text` alongside `imageBase64`.
- `FunctionsMealAnalyzer`: `buildMap` already adds `text` only when non-null — so a
  note passed as `text` is included in the payload automatically.
- Backend `validation.ts`: for `inputType="image"` it ignores `text` and returns only
  `{ inputType, imageBase64, date }`.
- Backend `prompts.ts`: `mealImagePrompt()` returns a fixed
  `"Analyze the food in this image."` with no user text.
- Coil (`libs.coil.compose`) is already a dependency and used in
  `AddBodyMeasurementScreen.kt` (`AsyncImage`) — no new dependency needed.

## Decisions (from brainstorming)

1. **UX:** a new preview screen after the image is chosen (not reusing the existing
   text field).
2. **Approach:** encode the image to base64 immediately on selection and hold it in the
   ViewModel during preview (Approach A) — reuses existing encode logic, moves IO off
   the send action.
3. **Preview actions:** minimal — **Send** + **Cancel/Back** (no "retake" button).
4. **Note persistence:** if the user writes a note, it becomes the saved meal
   `description`; if blank, the existing default (`"ארוחה מנותחת AI"`) stays.
5. **Note length:** soft limit in UI, hard limit ~500 chars in server validation.

## Design

### 1. UI / State machine (`AddMealScreen.kt`, `AddMealViewModel.kt`)

New step: `AddMealStep.ImagePreview`.

```
InputSelection → [pick/capture image] → ImagePreview → [Send] → Loading → ResultState
                                            ↓ [Cancel/Back]
                                        InputSelection
```

New composable `ImagePreviewContent` shows:
- The image via Coil `AsyncImage` bound to the pending `Uri`.
- An optional multiline text field, placeholder e.g.
  `"משהו שכדאי לדעת על המנה? (אופציונלי)"`, soft-capped at ~500 chars.
- Primary button `"שלח לניתוח AI 🚀"`.
- Back/cancel affordance → returns to `InputSelection` and clears image + note state.

Text-only flow (`analyzeText`) is unchanged.

### 2. ViewModel (`AddMealViewModel.kt`)

New state:
- `private var pendingImageBase64: String?`
- `pendingImageUri: StateFlow<Uri?>` (for display)
- `imageNote: StateFlow<String>` + `onImageNoteChange(note)`

Function changes:
- Rename `analyzeImageUri(context, uri)` → `prepareImage(context, uri)`: encodes to
  base64 on `Dispatchers.IO`, stores base64 + `Uri`, clears any prior note, moves to
  `ImagePreview`. On encode failure → error message + back to `InputSelection`
  (same as today).
- New `sendImageForAnalysis()`: calls
  `runAnalysis(inputType = "image", text = note.ifBlank { null }, imageBase64 = base64)`.
  Reuses existing `runAnalysis` (handles Loading/ResultState/errors).
- After a successful image analysis, if the note is non-blank, set `_mealDescription`
  to the note so `saveMeal` persists it as `description`. If blank, leave default.
- Clear pending state on cancel/back and in `resetToInput`.

### 3. Client analysis layer

- `MealAnalyzer.analyze`: **no signature change** (already supports `text`).
- `FunctionsMealAnalyzer`: **no code change** (`buildMap` already conditionally adds
  `text`). The note flows through as `text` with `inputType="image"`.

### 4. Backend (Cloud Functions)

- `validation.ts`: in the `image` branch, optionally read `text`. If present and
  non-empty → include `text: text.trim()` (after enforcing a max length, ~500 chars →
  `ValidationError` if exceeded). If missing/empty → omit (current behavior). Text-only
  validation unchanged (text required).
- `prompts.ts`: `mealImagePrompt()` → `mealImagePrompt(note?: string)`. With a note,
  append it as context, e.g.:
  > `Analyze the food in this image. The user added this note: "<note>". Use it as context (e.g. ingredients, portion, preparation) but rely on the image as the primary source.`

  Without a note → existing fixed text.
- `analyzeMeal.ts` (`runGemini`): for image input pass `mealImagePrompt(req.text)`;
  `inlineData` unchanged.

## Testing

**Backend (Jest, `functions/test/`):**
- `validation.test.ts`: image + valid text → `text` trimmed & kept; image without text →
  no `text`; image + blank/whitespace text → no `text`; image + over-length text →
  `ValidationError`.
- `mealImagePrompt(note)`: with note contains the note and the "rely on the image as
  primary" instruction; without note → fixed text.

**Client (`AddMealViewModelTest.kt`):**
- `prepareImage` success → step `ImagePreview`, `pendingImageUri` set.
- encode failure → error message + back to `InputSelection`.
- `sendImageForAnalysis` with note → analyzer called with `inputType="image"`,
  `text=<note>`, base64; after success `description` saved as the note.
- `sendImageForAnalysis` without note → analyzer called with `text=null`; description
  stays default.
- cancel/back clears pending state.

## Out of scope

- "Retake / choose another photo" directly from preview (user chose minimal actions).
- Changing the text-only flow.
- Storing the image itself (project rule: images are never stored).

## Iron rules honored

- AI keys/calls stay server-side; note travels only via the existing `analyzeMeal`
  callable. Image is sent for analysis and discarded, not stored. No blocking calls on
  the main thread (encoding stays on `Dispatchers.IO`).
