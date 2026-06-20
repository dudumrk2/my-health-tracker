# Optional Note on Meal Photo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user add a few optional words about a meal photo before it is sent to the AI, improving analysis accuracy and (optionally) saving the note as the meal description.

**Architecture:** Insert a new `ImagePreview` step between image selection and AI analysis. The image is encoded to base64 immediately on selection and held in the ViewModel alongside its `Uri` and an optional note. On "Send", the note is passed as the existing `text` parameter of `MealAnalyzer.analyze` together with the base64 image. The backend accepts an optional `text` for image input and folds it into the image prompt as context.

**Tech Stack:** Kotlin, Jetpack Compose, MVVM, Coroutines/Flow, Coil (already present), Firebase Functions (TypeScript), Vertex AI Gemini, JUnit4 + kotlinx-coroutines-test (client), Jest + ts-jest (backend).

## Global Constraints

- AI keys/calls stay server-side only; the note travels solely via the existing `analyzeMeal` callable.
- Images are never stored; the photo is sent for analysis and discarded.
- No blocking I/O on the main thread; base64 encoding stays on `Dispatchers.IO`.
- User-facing strings are Hebrew (RTL); code/comments in English.
- Note max length: **500 characters** (hard limit server-side, soft limit in UI).
- Backend tests: run from `functions/` with `npm test` (jest). Node is at `C:\nvm4w\nodejs` (not on PATH) — prefix commands with that on PATH if `npm`/`node` are not found.
- Client tests: `./gradlew test` (unit). Use `--tests` filter to target a single class.

---

## File Structure

- `functions/src/validation.ts` — accept optional `text` (≤500 chars) for image input.
- `functions/src/prompts.ts` — `mealImagePrompt(note?: string)` folds the note into the image prompt.
- `functions/src/analyzeMeal.ts` — pass `req.text` into `mealImagePrompt` for image input.
- `functions/test/validation.test.ts` — new cases for image + text.
- `functions/test/prompt.test.ts` — new cases for `mealImagePrompt(note)`.
- `app/.../ui/meal/AddMealViewModel.kt` — new `ImagePreview` step, pending image state, `prepareImage`/`sendImageForAnalysis`.
- `app/.../ui/meal/AddMealScreen.kt` — new `ImagePreviewContent` composable + wiring of launchers to `prepareImage`.
- `app/src/test/.../ui/meal/AddMealViewModelTest.kt` — capture `text` in FakeAnalyzer + new VM tests.

The backend tasks (1–2) are independent of the client tasks (3–5) and can be done in either order. Within the client, Task 3 (VM) precedes Task 4 (UI) which precedes Task 5 (UI wiring), but Task 3's tests are self-contained.

---

## Task 1: Backend — accept optional note for image input (validation)

**Files:**
- Modify: `functions/src/validation.ts`
- Test: `functions/test/validation.test.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AnalyzeMealRequest` gains an optional `text?: string` that is now populated for `inputType="image"` when a non-empty, ≤500-char note is supplied.

- [ ] **Step 1: Write the failing tests**

Add these cases inside the `describe("validateRequest", ...)` block in `functions/test/validation.test.ts`:

```typescript
it("keeps a trimmed note on an image request", () => {
  const r = validateRequest({ inputType: "image", imageBase64: "abc", text: "  עם רוטב טחינה  ", date: "2026-06-13" });
  expect(r.inputType).toBe("image");
  expect(r.imageBase64).toBe("abc");
  expect(r.text).toBe("עם רוטב טחינה");
});

it("omits the note on an image request when it is blank", () => {
  const r = validateRequest({ inputType: "image", imageBase64: "abc", text: "   ", date: "2026-06-13" });
  expect(r.text).toBeUndefined();
});

it("omits the note on an image request when it is absent", () => {
  const r = validateRequest({ inputType: "image", imageBase64: "abc", date: "2026-06-13" });
  expect(r.text).toBeUndefined();
});

it("rejects an image note longer than 500 characters", () => {
  const long = "a".repeat(501);
  expect(() => validateRequest({ inputType: "image", imageBase64: "abc", text: long, date: "2026-06-13" })).toThrow(ValidationError);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- validation` (from `functions/`)
Expected: FAIL — the new image+text assertions fail (`r.text` is `undefined` where a value is expected; over-length case does not throw).

- [ ] **Step 3: Implement the validation change**

In `functions/src/validation.ts`, add a constant and replace the image branch. The final file:

```typescript
export class ValidationError extends Error {}

export interface AnalyzeMealRequest {
  inputType: "text" | "image";
  text?: string;
  imageBase64?: string;
  date: string;
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_NOTE_LENGTH = 500;

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

  // Optional free-text note accompanying the image.
  const note = d.text;
  if (typeof note === "string") {
    const trimmed = note.trim();
    if (trimmed.length > MAX_NOTE_LENGTH) {
      throw new ValidationError(`note must be at most ${MAX_NOTE_LENGTH} characters.`);
    }
    if (trimmed.length > 0) {
      return { inputType, imageBase64, text: trimmed, date };
    }
  }
  return { inputType, imageBase64, date };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- validation` (from `functions/`)
Expected: PASS — all existing and new `validateRequest` cases pass.

- [ ] **Step 5: Commit**

```bash
git add functions/src/validation.ts functions/test/validation.test.ts
git commit -m "feat(meal): accept optional note (<=500 chars) for image analysis requests"
```

---

## Task 2: Backend — fold note into the image prompt

**Files:**
- Modify: `functions/src/prompts.ts:88-91`
- Modify: `functions/src/analyzeMeal.ts:50-56`
- Test: `functions/test/prompt.test.ts`

**Interfaces:**
- Consumes: `AnalyzeMealRequest.text` (from Task 1).
- Produces: `mealImagePrompt(note?: string): string` — includes the note and a "rely on the image as the primary source" instruction when a note is given; otherwise the fixed text.

- [ ] **Step 1: Write the failing tests**

Add to `functions/test/prompt.test.ts`. First extend the import on line 1–7 to include `mealImagePrompt`:

```typescript
import {
  buildMealSystemInstruction,
  MEAL_RESPONSE_SCHEMA,
  ProfileContext,
  buildInsightsSystemInstruction,
  buildInsightsUserPrompt,
  mealImagePrompt,
} from "../src/prompts";
```

Then add this describe block at the end of the file:

```typescript
describe("mealImagePrompt", () => {
  it("returns the fixed instruction when no note is given", () => {
    expect(mealImagePrompt()).toBe("Analyze the food in this image.");
  });

  it("includes the note and keeps the image as the primary source", () => {
    const p = mealImagePrompt("עם רוטב טחינה");
    expect(p).toContain("עם רוטב טחינה");
    expect(p.toLowerCase()).toContain("primary source");
  });

  it("treats a blank note as no note", () => {
    expect(mealImagePrompt("   ")).toBe("Analyze the food in this image.");
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- prompt` (from `functions/`)
Expected: FAIL — `mealImagePrompt` currently takes no argument and ignores the note; the note/`primary source` assertions fail.

- [ ] **Step 3: Implement the prompt change**

Replace `functions/src/prompts.ts:88-91` with:

```typescript
/** User-turn text accompanying an inline meal image, optionally with a user note. */
export function mealImagePrompt(note?: string): string {
  const base = "Analyze the food in this image.";
  const trimmed = note?.trim();
  if (!trimmed) return base;
  return `${base} The user added this note: "${trimmed}". Use it as context (e.g. ingredients, portion, preparation) but rely on the image as the primary source.`;
}
```

- [ ] **Step 4: Wire the note into `runGemini`**

In `functions/src/analyzeMeal.ts`, change the image branch (currently lines 51-56) so the image prompt receives the note:

```typescript
  const parts =
    req.inputType === "image"
      ? [
          { text: mealImagePrompt(req.text) },
          { inlineData: { mimeType: "image/jpeg", data: req.imageBase64! } },
        ]
      : [{ text: mealTextPrompt(req.text!) }];
```

- [ ] **Step 5: Run tests and type-check**

Run: `npm test -- prompt` then `npm run build` (from `functions/`)
Expected: PASS for prompt tests; `npm run build` (tsc) completes with no errors.

- [ ] **Step 6: Commit**

```bash
git add functions/src/prompts.ts functions/src/analyzeMeal.ts functions/test/prompt.test.ts
git commit -m "feat(meal): fold optional user note into the image analysis prompt"
```

---

## Task 3: Client — ViewModel preview step and note plumbing

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

**Interfaces:**
- Consumes: existing `MealAnalyzer.analyze(inputType, text, imageBase64, date)`, `ImageEncoder.uriToBase64Jpeg(context, uri): String?`.
- Produces (used by Task 4/5 UI):
  - `sealed AddMealStep.ImagePreview`
  - `pendingImageUri: StateFlow<Uri?>`
  - `imageNote: StateFlow<String>`
  - `fun onImageNoteChange(note: String)`
  - `fun prepareImage(context: Context, uri: Uri)` — encodes + moves to `ImagePreview`
  - `fun sendImageForAnalysis()` — analyzes pending image with the note
  - `fun cancelImagePreview()` — clears pending state, back to `InputSelection`

- [ ] **Step 1: Update FakeAnalyzer to capture call arguments**

In `AddMealViewModelTest.kt`, replace the `FakeAnalyzer` class (lines 31-39) so tests can assert what was passed:

```kotlin
    private class FakeAnalyzer(
        var result: MealAnalysisResult? = null,
        var error: String? = null
    ) : MealAnalyzer {
        var lastInputType: String? = null
        var lastText: String? = null
        var lastImageBase64: String? = null
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            lastInputType = inputType
            lastText = text
            lastImageBase64 = imageBase64
            error?.let { throw MealAnalysisException(it) }
            return result!!
        }
    }
```

- [ ] **Step 2: Write the failing tests**

Add these tests to `AddMealViewModelTest.kt`. They drive `sendImageForAnalysis` directly (image encoding via `prepareImage` needs an Android `Context`/`Uri`, which is covered by instrumented/manual testing; these unit tests exercise the note→analyzer→description logic by seeding the note and a fake base64 through a small test seam).

Add a helper near the other helpers and the tests:

```kotlin
    @Test
    fun `sendImageForAnalysis passes the note as text and saves it as description`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Apple", "1", 95, 0, 25, 0)),
                totals = MealTotals(95, 0, 25, 0),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.seedPendingImageForTest("base64data")
        vm.onImageNoteChange("תפוח אורגני")
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(AddMealStep.ResultState, vm.step.value)
        assertEquals("image", analyzer.lastInputType)
        assertEquals("תפוח אורגני", analyzer.lastText)
        assertEquals("base64data", analyzer.lastImageBase64)
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals("תפוח אורגני", repo.saved.first().description)
    }

    @Test
    fun `sendImageForAnalysis with blank note passes null text and keeps default description`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Apple", "1", 95, 0, 25, 0)),
                totals = MealTotals(95, 0, 25, 0),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.seedPendingImageForTest("base64data")
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(null, analyzer.lastText)
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals("ארוחה מנותחת AI", repo.saved.first().description)
    }

    @Test
    fun `cancelImagePreview clears note and returns to input`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer())
        vm.seedPendingImageForTest("base64data")
        vm.onImageNoteChange("something")
        vm.cancelImagePreview()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals("", vm.imageNote.value)
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: FAIL/compile error — `AddMealStep.ImagePreview`, `seedPendingImageForTest`, `onImageNoteChange`, `imageNote`, `sendImageForAnalysis`, `cancelImagePreview` do not exist yet.

- [ ] **Step 4: Implement the ViewModel changes**

In `AddMealViewModel.kt`:

(a) Add the step to the sealed class (after `ManualFallback`):

```kotlin
sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object ImagePreview : AddMealStep()
    object Loading : AddMealStep()
    object ResultState : AddMealStep()
    object ManualFallback : AddMealStep()
}
```

(b) Add new state near the other `MutableStateFlow` declarations:

```kotlin
    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    private val _imageNote = MutableStateFlow("")
    val imageNote: StateFlow<String> = _imageNote.asStateFlow()

    // Held only during the ImagePreview step; cleared on send/cancel.
    private var pendingImageBase64: String? = null

    fun onImageNoteChange(note: String) { _imageNote.value = note }

    // Test seam: lets unit tests stage a pending image without an Android Uri/encode.
    internal fun seedPendingImageForTest(base64: String) {
        pendingImageBase64 = base64
        _step.value = AddMealStep.ImagePreview
    }
```

(c) Replace `analyzeImageUri` (lines 99-125) with `prepareImage` that encodes and moves to preview:

```kotlin
    fun prepareImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _errorMessage.value = null
            _imageNote.value = ""
            _step.value = AddMealStep.Loading
            val base64 = withContext(Dispatchers.IO) {
                com.myhealthtracker.app.util.ImageEncoder.uriToBase64Jpeg(context, uri)
            }
            if (base64 == null) {
                _errorMessage.value = "שגיאה בעיבוד התמונה"
                _step.value = AddMealStep.InputSelection
                return@launch
            }
            pendingImageBase64 = base64
            _pendingImageUri.value = uri
            _step.value = AddMealStep.ImagePreview
        }
    }

    fun sendImageForAnalysis() {
        val base64 = pendingImageBase64 ?: return
        val note = _imageNote.value.trim()
        if (note.isNotEmpty()) {
            _mealDescription.value = note
        }
        runAnalysis(inputType = "image", text = note.ifEmpty { null }, imageBase64 = base64)
    }

    fun cancelImagePreview() {
        _errorMessage.value = null
        _imageNote.value = ""
        pendingImageBase64 = null
        _pendingImageUri.value = null
        _step.value = AddMealStep.InputSelection
    }
```

(d) In `runAnalysis`, after a successful image analysis the pending base64 is no longer needed — clear it on success. Add `pendingImageBase64 = null` and `_pendingImageUri.value = null` right after `lastInputType = inputType` inside the `try` of `runAnalysis`:

```kotlin
            try {
                val result = analyzer.analyze(inputType, text, imageBase64, today())
                lastInputType = inputType
                pendingImageBase64 = null
                _pendingImageUri.value = null
                _recognizedItems.value = result.items
                _excludedIndices.value = emptySet()
                _lowConfidence.value = result.lowConfidence
                _recommendation.value = result.recommendation
                _quality.value = result.quality
                _step.value = AddMealStep.ResultState
            } catch (e: MealAnalysisException) {
                _errorMessage.value = e.message
                _step.value = AddMealStep.InputSelection
            }
```

(e) In `resetToInput`, also clear the pending image/note:

```kotlin
    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
        _recommendation.value = null
        _quality.value = null
        _excludedIndices.value = emptySet()
        _imageNote.value = ""
        pendingImageBase64 = null
        _pendingImageUri.value = null
    }
```

Note: `analyzeImage(imageBase64: String)` (the direct-base64 entry used by an existing test) stays as-is so the existing `successful image analysis` test keeps passing.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: PASS — all existing and new VM tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
git commit -m "feat(meal): ViewModel image-preview step with optional note"
```

---

## Task 4: Client — ImagePreview composable

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`

**Interfaces:**
- Consumes: `pendingImageUri`, `imageNote`, `onImageNoteChange`, `sendImageForAnalysis`, `cancelImagePreview` from Task 3; Coil `coil.compose.AsyncImage`.
- Produces: `ImagePreviewContent` composable, rendered when `step == AddMealStep.ImagePreview`.

This task is UI-only; it is verified by build + Compose preview rather than unit tests (consistent with the existing screen, which has `@Preview`s and no Compose UI tests).

- [ ] **Step 1: Add the import**

Add near the other imports at the top of `AddMealScreen.kt`:

```kotlin
import coil.compose.AsyncImage
```

- [ ] **Step 2: Add the `ImagePreviewContent` composable**

Add this composable after `InputSelectionContent` (before `LoadingContent`):

```kotlin
// 1b. Image Preview Step — show the chosen photo + optional note before AI analysis
@Composable
private fun ImagePreviewContent(
    imageUri: android.net.Uri?,
    note: String,
    errorMessage: String?,
    onNoteChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "תצוגה מקדימה",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "תצוגה מקדימה של הארוחה",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) onNoteChange(it) },
                placeholder = { Text("משהו שכדאי לדעת על המנה? (אופציונלי)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onSendClick,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("שלח לניתוח AI 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            TextButton(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("חזרה", fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

- [ ] **Step 3: Add a Compose preview**

Add after the existing `AddMealScreenPreviewInput` preview:

```kotlin
@Preview(showBackground = true, name = "Image Preview Step")
@Composable
fun AddMealScreenPreviewImagePreview() {
    MyHealthTrackerTheme {
        ImagePreviewContent(
            imageUri = null,
            note = "עם רוטב טחינה",
            errorMessage = null,
            onNoteChange = {},
            onSendClick = {},
            onCancelClick = {}
        )
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt
git commit -m "feat(meal): ImagePreview composable with optional note field"
```

---

## Task 5: Client — wire launchers and step rendering to the preview

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`

**Interfaces:**
- Consumes: `ImagePreviewContent` (Task 4), `prepareImage`/`sendImageForAnalysis`/`cancelImagePreview`/`pendingImageUri`/`imageNote` (Task 3).
- Produces: full end-to-end flow InputSelection → ImagePreview → analysis.

- [ ] **Step 1: Collect the new state in `AddMealScreen`**

In `AddMealScreen`, alongside the other `collectAsState()` calls (around lines 85-99), add:

```kotlin
    val pendingImageUri by viewModel.pendingImageUri.collectAsState()
    val imageNote by viewModel.imageNote.collectAsState()
```

- [ ] **Step 2: Point the camera and gallery launchers at `prepareImage`**

Replace the two `viewModel.analyzeImageUri(...)` call sites with `viewModel.prepareImage(...)`:

In `cameraLauncher` (currently line 114):

```kotlin
            if (success && uri != null) {
                viewModel.prepareImage(context.applicationContext, uri)
            }
```

In `galleryLauncher` (currently line 147):

```kotlin
        if (uri != null) {
            viewModel.prepareImage(context, uri)
        }
```

- [ ] **Step 3: Render the new step in the `when (step)` block**

Add an `AddMealStep.ImagePreview` branch inside the `when (step)` (after `InputSelection`, before `Loading`):

```kotlin
            AddMealStep.ImagePreview -> {
                ImagePreviewContent(
                    imageUri = pendingImageUri,
                    note = imageNote,
                    errorMessage = errorMessage,
                    onNoteChange = { viewModel.onImageNoteChange(it) },
                    onSendClick = { viewModel.sendImageForAnalysis() },
                    onCancelClick = { viewModel.cancelImagePreview() },
                    modifier = contentModifier
                )
            }
```

- [ ] **Step 4: Verify the full module compiles and all unit tests pass**

Run: `./gradlew :app:compileDebugKotlin` then `./gradlew test --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: BUILD SUCCESSFUL; tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt
git commit -m "feat(meal): route image selection through the preview step"
```

---

## Task 6: Docs — changelog entry

**Files:**
- Modify: `docs/CHANGELOG.md`

- [ ] **Step 1: Add an entry**

Add a bullet under the most recent/Unreleased section of `docs/CHANGELOG.md` (match the existing format in that file):

```markdown
- feat(meal): optional note on a meal photo — a preview step lets the user add a few words before the image is sent to the AI; the note is used as analysis context and (if provided) saved as the meal description.
```

- [ ] **Step 2: Commit**

```bash
git add docs/CHANGELOG.md
git commit -m "docs: changelog for optional meal-photo note"
```

---

## Self-Review

**Spec coverage:**
- New `ImagePreview` step + preview screen → Tasks 3, 4, 5. ✓
- Encode immediately, hold base64 in VM (Approach A) → Task 3 `prepareImage`. ✓
- Minimal actions (Send + Cancel/Back) → Task 4 buttons, Task 3 `sendImageForAnalysis`/`cancelImagePreview`. ✓
- Note becomes description when non-blank, default otherwise → Task 3 `sendImageForAnalysis` + tests. ✓
- Note travels as `text` via existing `MealAnalyzer`/`FunctionsMealAnalyzer` (no change) → confirmed in spec; no task needed (existing `buildMap` already conditional). ✓
- Backend validation accepts optional `text` ≤500 for image → Task 1. ✓
- `mealImagePrompt(note)` + `runGemini` wiring → Task 2. ✓
- Tests (backend validation, prompt; client VM) → Tasks 1, 2, 3. ✓
- Iron rules (no image storage, server-side AI, IO off main thread) → preserved; no new storage; encode stays on `Dispatchers.IO`. ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases"; all code steps contain full code. ✓

**Type consistency:** `prepareImage(context, uri)`, `sendImageForAnalysis()`, `cancelImagePreview()`, `onImageNoteChange(note)`, `pendingImageUri`, `imageNote`, `AddMealStep.ImagePreview`, `mealImagePrompt(note?)`, `AnalyzeMealRequest.text` — names match across tasks. `seedPendingImageForTest` defined in Task 3 Step 4 and used in Task 3 Step 2 tests. ✓

**Note on FunctionsMealAnalyzer:** No code change required — `buildMap` already adds `text` only when non-null, and Task 3 passes the note as `text`. The client→server payload therefore carries the note automatically. (Verified in spec Background.)
