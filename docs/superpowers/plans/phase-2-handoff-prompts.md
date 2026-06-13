# Phase 2 — Handoff Prompts (Tasks 5–17)

This document lets a fresh agent continue executing the Phase 2 plan. Tasks 1–4 are already done and committed.

## Current state

- **Branch:** `feat/phase-2-meal-ai` (do NOT commit to `main`).
- **Plan (verbatim code per task):** `docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md`
- **Spec:** `docs/superpowers/specs/2026-06-13-phase-2-meal-logging-ai-design.md`
- **Done & committed:**
  - Task 1 — scaffold `functions/` + `firebase.json` + `firestore.rules` (SHA `a78f194`)
  - Task 2 — `functions/src/validation.ts` + test, 7 passing (SHA `12f6f92`)
  - Task 3 — `functions/src/prompt.ts` + test, 3 passing (SHA `6f8bbcc`)
  - Task 4 — `functions/src/parse.ts` + test, 5 passing (SHA `c76ee3e`)
  - Base commit before Phase 2 code: plan commit `2712595`.
- **Remaining:** Tasks 5–17 (below).

## Execution method (subagent-driven)

For each task: dispatch one implementer subagent with the prompt below → when it reports DONE, independently verify (read the committed diff + run the task's tests/build, don't just trust the report) → then move to the next task. Run continuously; only stop at the two manual hand-off points.

- **Model:** pure backend / mechanical tasks (5, 7, 13, 16) → a fast/cheap model is fine. Integration/multi-file tasks (6, 8, 9, 10, 11, 12, 14, 15, 17) → standard model.
- The plan file contains the **exact, verbatim code** for every step. Implementers should copy it precisely.

## Environment gotchas (already learned)

- Windows. Use the Bash tool (Git Bash) or PowerShell. `npm --prefix functions <cmd>` works from repo root.
- `functions/tsconfig.json` has `strict` + **`noUnusedLocals: true`** — no unused imports/vars or `tsc` fails.
- `SchemaType` IS exported by the installed `@google-cloud/vertexai` — confirmed working.
- Gradle wrapper: `./gradlew` (bash) or `.\gradlew.bat` (PowerShell). Project rules give blanket approval for npm/gradle in `D:\AICode`.
- Android module has `buildConfig = false` — there is no `BuildConfig.DEBUG`; Task 8 detects debug via `ApplicationInfo.FLAG_DEBUGGABLE` (already in the plan).
- Firebase BOM is `33.1.1`; App Check artifacts are versionless under the BOM. If `firebase-appcheck-playintegrity`/`-debug` fail to resolve under this BOM, report it (don't pin a random version) — it likely just needs a sync.
- Commit trailer for every commit: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

## TWO MANUAL STOP POINTS (do not perform these — hand to the user)

1. **After Task 6:** do NOT run `firebase deploy`, do NOT enable Vertex AI API, do NOT configure App Check.
2. **At Task 17 Step 7:** stop and list the manual steps for the user (deploy, enable Vertex AI, App Check, `.firebaserc` project id).

---

## TASK 5 — Vertex call with timeout + retry (TDD)

```
You are implementing Task 5 of a Cloud Functions backend: "Vertex call with timeout + retry (TDD)".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
The functions/ TS project exists (strict, noUnusedLocals, ts-jest). Follow strict TDD.

Implement EXACTLY as specified in Task 5 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
The plan contains the verbatim code for:
  - functions/test/vertexClient.test.ts  (7 tests)
  - functions/src/vertexClient.ts  (TimeoutError, isRetryable, withTimeout, callWithRetry)
Copy them exactly.

Steps:
1. Create the test file from the plan; run `npm --prefix functions test -- vertexClient`; confirm FAIL (module not found).
2. Create functions/src/vertexClient.ts from the plan; run the test again; confirm PASS (7 tests).
3. Commit: git add functions/src/vertexClient.ts functions/test/vertexClient.test.ts
   message: feat(functions): add Vertex timeout + retry helper
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: tests pass, no unused imports, clean tree.
Report: Status, test pass count, commit SHA (git rev-parse HEAD), files changed.
```

---

## TASK 6 — Wire the analyzeMeal callable  ⚠ STOP POINT AFTER THIS TASK

```
You are implementing Task 6 of a Cloud Functions backend: "Wire the analyzeMeal callable".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
Tasks 2–5 produced: src/validation.ts, src/prompt.ts, src/parse.ts, src/vertexClient.ts. This task is the
integration glue that uses all of them. No new unit test (covered by tsc build + later emulator).

Implement EXACTLY as specified in Task 6 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
Create from the plan, verbatim:
  - functions/src/analyzeMeal.ts  (onCall, enforceAppCheck, auth check, readProfile, runGemini, error envelope, logging)
  - functions/src/index.ts        (export { analyzeMeal })

Steps:
1. Create both files from the plan verbatim.
2. Build: `npm --prefix functions run build` — must compile with no tsc errors. If the @google-cloud/vertexai
   or firebase-functions v2 API surface differs from the plan (e.g. responseSchema typing, generateContent shape),
   fix the minimal typing to compile WITHOUT changing behavior, and note it as DONE_WITH_CONCERNS.
3. Run the full suite: `npm --prefix functions test` — all prior suites must still pass.
4. Commit: git add functions/src/analyzeMeal.ts functions/src/index.ts
   message: feat(functions): wire analyzeMeal callable (App Check, profile, Vertex, error envelope)
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: build clean, all tests pass, secrets/imageBase64 never logged.
Report: Status, build result, test totals, commit SHA, files changed, any typing deviations.

IMPORTANT: Do NOT run `firebase deploy`, do NOT enable Vertex AI API, do NOT configure App Check. Those are the user's manual steps. After this task, the CONTROLLER must pause and tell the user the backend is code-complete and these manual steps are pending before the function can actually run.
```

---

## TASK 7 — Add Functions + App Check dependencies (Android)

```
You are implementing Task 7: "Add Functions + App Check dependencies" to an Android Gradle project.
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).

Implement EXACTLY as specified in Task 7 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.

Steps:
1. In gradle/libs.versions.toml, under [libraries] after firebase-firestore, add:
     firebase-functions = { group = "com.google.firebase", name = "firebase-functions" }
     firebase-appcheck-playintegrity = { group = "com.google.firebase", name = "firebase-appcheck-playintegrity" }
     firebase-appcheck-debug = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
2. In app/build.gradle.kts, in the Firebase block after `implementation(libs.play.services.auth)`, add:
     implementation(libs.firebase.functions)
     implementation(libs.firebase.appcheck.playintegrity)
     debugImplementation(libs.firebase.appcheck.debug)
3. Verify resolution: `./gradlew :app:dependencies --configuration debugRuntimeClasspath` (bash) — the firebase-functions
   and firebase-appcheck artifacts must resolve with no errors. (Gradle/network allowed under D:\AICode rules.)
   If they do not resolve under firebase-bom 33.1.1, report BLOCKED with the exact gradle error (do not guess a version).
4. Commit: git add gradle/libs.versions.toml app/build.gradle.kts
   message: build: add Firebase Functions + App Check dependencies
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Report: Status, dependency-resolution result, commit SHA, files changed.
```

---

## TASK 8 — Application class + App Check + service locator (Android)

```
You are implementing Task 8: "Application class + App Check + service locator".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
NOTE: the Android module has buildConfig disabled (no BuildConfig.DEBUG); the plan detects debug via
ApplicationInfo.FLAG_DEBUGGABLE — use the plan code verbatim.

Implement EXACTLY as specified in Task 8 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
Create verbatim:
  - app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt
    (initially points mealRepository/waterRepository at FakeRepository — this is intentional; Task 11 flips it)
  - app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt  (App Check: Debug provider in debug, PlayIntegrity in release)
Modify:
  - app/src/main/AndroidManifest.xml — add android:name=".MyHealthApp" to the <application> tag.

Steps:
1. Create the two files + manifest edit from the plan verbatim.
2. Build: `./gradlew :app:compileDebugKotlin` — must be BUILD SUCCESSFUL. If the App Check ktx import path
   (com.google.firebase.appcheck.ktx.appCheck / com.google.firebase.ktx.Firebase) differs in this Firebase BOM,
   adjust the import to the correct one for the resolved version so it compiles, and note it (DONE_WITH_CONCERNS).
3. Commit: git add app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt app/src/main/java/com/myhealthtracker/app/MyHealthApp.kt app/src/main/AndroidManifest.xml
   message: feat: add Application class with App Check + AppContainer service locator
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: compiles, manifest registers the Application, no behavior beyond spec.
Report: Status, build result, commit SHA, files changed, any import deviations.
```

---

## TASK 9 — MealAnalyzer interface + Functions implementation (TDD, Android)

```
You are implementing Task 9: "MealAnalyzer interface + Functions-backed implementation" (TDD).
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
Existing unit tests use JUnit4 + MockK + kotlinx-coroutines-test. The pure mapping function `mapAnalyzeResponse`
is what gets unit-tested; the network call lives in FunctionsMealAnalyzer behind the MealAnalyzer interface.

Implement EXACTLY as specified in Task 9 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
TDD order:
1. Create test app/src/test/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzerTest.kt (3 tests) from the plan.
2. Run `./gradlew :app:testDebugUnitTest --tests "*FunctionsMealAnalyzerTest"` — confirm FAIL (unresolved references).
3. Create app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalyzer.kt
   (MealAnalysisResult, MealAnalysisException, MealAnalyzer interface) from the plan.
4. Create app/src/main/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzer.kt
   (mapAnalyzeResponse pure fn + FunctionsMealAnalyzer using FirebaseFunctions callable + kotlinx-coroutines-play-services await) from the plan.
   NOTE: `kotlinx.coroutines.tasks.await` requires the `kotlinx-coroutines-play-services` artifact. If it is NOT already on
   the classpath (check: try to compile), add it to gradle/libs.versions.toml + app/build.gradle.kts as
   `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")` (matches existing coroutines version) and note it.
5. Run the test again — confirm PASS (3 tests).
6. Commit: git add app/src/main/java/com/myhealthtracker/app/data/meal/MealAnalyzer.kt app/src/main/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzer.kt app/src/test/java/com/myhealthtracker/app/data/meal/FunctionsMealAnalyzerTest.kt (plus gradle files if you added the coroutines-play-services dep)
   message: feat: add MealAnalyzer interface + Functions-backed implementation
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: tests pass, error-code mapping matches plan, Hebrew user-facing strings preserved.
Report: Status, test pass count, commit SHA, files changed, any added dependency.
```

---

## TASK 10 — FirestoreMealRepository (Android)

```
You are implementing Task 10: "FirestoreMealRepository".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
This implements the existing MealRepository interface (val meals: StateFlow<List<MealEntry>>, addMeal(...), deleteMeal(...))
backed by Firestore + a snapshot listener scoped to the signed-in user. Verified by build now and the emulator test in Task 12.

Implement EXACTLY as specified in Task 10 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
Create verbatim:
  - app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt
    (includes private DocumentSnapshot.toMealEntry() and MealItem/MealTotals .toMap() helpers from the plan)

Steps:
1. Create the file from the plan verbatim.
2. Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
3. Commit: git add app/src/main/java/com/myhealthtracker/app/data/meal/FirestoreMealRepository.kt
   message: feat: add FirestoreMealRepository
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: implements MealRepository signatures exactly; document field names match the data model
(date, loggedAt, inputType, description, items[], totals, aiModel).
Report: Status, build result, commit SHA, files changed.
```

---

## TASK 11 — FirestoreWaterRepository + flip the service locator (Android)

```
You are implementing Task 11: "FirestoreWaterRepository + flip AppContainer to Firestore repos".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
Water stays in ML (field amountMl, idempotent FieldValue.increment). This also switches AppContainer (from Task 8)
from FakeRepository to the real Firestore repos + FunctionsMealAnalyzer (from Tasks 9, 10).

Implement EXACTLY as specified in Task 11 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
1. Create app/src/main/java/com/myhealthtracker/app/data/water/FirestoreWaterRepository.kt verbatim from the plan.
2. Replace app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt with the plan's Task 11 version:
     val mealRepository by lazy { FirestoreMealRepository() }
     val waterRepository by lazy { FirestoreWaterRepository() }
     val mealAnalyzer by lazy { FunctionsMealAnalyzer() }
3. Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
4. Commit: git add app/src/main/java/com/myhealthtracker/app/data/water/FirestoreWaterRepository.kt app/src/main/java/com/myhealthtracker/app/di/AppContainer.kt
   message: feat: add FirestoreWaterRepository and switch AppContainer to Firestore repos
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: implements WaterRepository signatures; addWater uses FieldValue.increment on amountMl; listener maps amountMl back to Int.
Report: Status, build result, commit SHA, files changed.
```

---

## TASK 12 — Emulator instrumented test for Firestore repos (Android)

```
You are implementing Task 12: "Emulator instrumented test for Firestore meal write + water increment".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).

Implement EXACTLY as specified in Task 12 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
1. Create app/src/androidTest/java/com/myhealthtracker/app/FirestoreMealWaterEmulatorTest.kt verbatim from the plan
   (2 tests: waterIncrementIsIdempotentPerDate, mealWriteThenListenReturnsEntry). Ensure the import
   `import kotlinx.coroutines.tasks.await` is present (the plan notes this).
2. The test needs the Firestore + Auth emulators AND an Android device/emulator. If those are available:
     - Start emulators: `firebase emulators:start --only firestore,auth`
     - Run: `./gradlew :app:connectedDebugAndroidTest --tests "*FirestoreMealWaterEmulatorTest"` — expect 2 passing.
   If NO Android device/emulator (or firebase CLI) is available in this environment: do NOT block the whole task —
   confirm the test source compiles (`./gradlew :app:compileDebugAndroidTestKotlin`), commit it, and report
   DONE_WITH_CONCERNS noting the test could not be executed here and must be run on a device by the user.
3. Commit: git add app/src/androidTest/java/com/myhealthtracker/app/FirestoreMealWaterEmulatorTest.kt
   message: test: emulator tests for Firestore meal write + water increment
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Report: Status, whether tests ran (and result) or compile-only, commit SHA, files changed.
```

---

## TASK 13 — Image downscale + base64 encoder (Android)

```
You are implementing Task 13: "Image downscale + base64 encoder util".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).

Implement EXACTLY as specified in Task 13 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
1. Create app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt verbatim from the plan
   (uriToBase64Jpeg, bitmapToBase64Jpeg, private downscale; MAX_DIM=1024, JPEG quality 80; no image persisted).
2. Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
3. Commit: git add app/src/main/java/com/myhealthtracker/app/util/ImageEncoder.kt
   message: feat: add image downscale + base64 encoder util
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Report: Status, build result, commit SHA, files changed.
```

---

## TASK 14 — Rewrite AddMealViewModel against the real analyzer (TDD, Android)

```
You are implementing Task 14: "Rewrite AddMealViewModel to use the real analyzer + Firestore save" (TDD).
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
This is a full rewrite of an existing file that currently uses mock data + delay(). New constructor is
AddMealViewModel(mealRepository, analyzer); new API includes analyzeText(), analyzeImage(base64), lowConfidence StateFlow.

Implement EXACTLY as specified in Task 14 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
TDD order:
1. Create test app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt (4 tests) from the plan
   (uses FakeAnalyzer + FakeMealRepo, StandardTestDispatcher).
2. Run `./gradlew :app:testDebugUnitTest --tests "*AddMealViewModelTest"` — confirm FAIL (new signature/members missing).
3. Replace app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt with the plan's Task 14 version verbatim.
4. Run the test again — confirm PASS (4 tests).
5. This rewrite removes the old mock methods analyzeMeal(isImage)/the canned items. AddMealScreen still calls the OLD
   API and will not compile until Task 15. That's expected: for THIS task only run the unit test target
   (testDebugUnitTest), NOT a full assemble. Do not edit AddMealScreen here (that is Task 15).
6. Commit: git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
   message: feat: AddMealViewModel uses real analyzer + Firestore save (TDD)
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: 4 tests pass; empty-input validation, error->manual fallback, lowConfidence, save-writes-repo all covered.
Report: Status, test pass count, commit SHA, files changed. Note that AddMealScreen compile is intentionally deferred to Task 15.
```

---

## TASK 15 — Wire camera/gallery + lowConfidence banner in AddMealScreen (Android)

```
You are implementing Task 15: "Wire real camera/gallery capture + lowConfidence banner in AddMealScreen".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
After Task 14, AddMealScreen.kt still references the OLD ViewModel API and does not compile — this task fixes it and
adds real image input. This is the most intricate UI task; make the edits carefully and follow the plan's 10 steps.

Implement EXACTLY as specified in Task 15 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md, which covers:
  - res/xml/file_paths.xml (cache-path meal_images)
  - AndroidManifest.xml FileProvider (authority ${applicationId}.fileprovider)
  - AddMealScreen: collect lowConfidence; add gallery (PickVisualMedia) + camera (TakePicture w/ FileProvider temp file) launchers;
    createCameraImageUri() helper; update InputSelection branch to call viewModel.analyzeText() / analyzeImage(base64);
    change InputSelectionContent signature (onPickImageClick + onCameraClick replacing onImageClick) and its image UI (camera+gallery buttons);
    pass lowConfidence into ResultStateContent and render the warning banner; update both @Preview composables to the new signatures.
  - Images are encoded via ImageEncoder (Task 13) and the temp camera file is deleted after encoding.

Steps:
1. Apply all edits per the plan's Task 15 steps 1–8.
2. Build the whole debug app: `./gradlew :app:compileDebugKotlin` then `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
   (This is the first full compile since Task 14; it confirms screen↔viewModel are back in sync.)
3. Run the unit suite to confirm no regressions: `./gradlew :app:testDebugUnitTest`.
4. Commit: git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
   message: feat: wire camera/gallery capture + lowConfidence banner in AddMealScreen
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Self-review: app compiles & assembles; no CAMERA permission added (TakePicture uses external camera app); temp image deleted; previews updated.
Report: Status, compile + assemble + unit-test results, commit SHA, files changed. If you got stuck reconciling the
existing AddMealScreen structure with the plan edits, report BLOCKED with specifics rather than guessing.
```

---

## TASK 16 — Point FoodViewModel at the service locator (Android)

```
You are implementing Task 16: "Point FoodViewModel at the service locator".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).
Water stays ML; only the repo defaults change so the live screen reads/writes Firestore.

Implement EXACTLY as specified in Task 16 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
1. In app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt change the constructor defaults from
   `= FakeRepository` to `= AppContainer.mealRepository` / `= AppContainer.waterRepository`; remove the
   `import com.myhealthtracker.app.data.FakeRepository` and add `import com.myhealthtracker.app.di.AppContainer`.
2. Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
3. Regression: `./gradlew :app:testDebugUnitTest` — all pass.
4. Commit: git add app/src/main/java/com/myhealthtracker/app/ui/food/FoodViewModel.kt
   message: feat: FoodViewModel reads meals/water from Firestore via AppContainer
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Report: Status, build + test results, commit SHA, files changed.
```

---

## TASK 17 — Docs + final verification  ⚠ STOP POINT (Step 7)

```
You are implementing Task 17: "Docs + final verification".
Work from: d:\AICode\my-health-tracker\my-health-tracker on branch feat/phase-2-meal-ai (do not switch).

Implement EXACTLY as specified in Task 17 of docs/superpowers/plans/2026-06-13-phase-2-meal-logging-ai.md.
1. In CLAUDE.md, replace the water schema line `├── water/{date}      : { date, cups, updatedAt }` with:
   `├── water/{date}      : { date, amountMl, updatedAt }   (כמות מצטברת במ"ל, idempotent — הגדלה ב-FieldValue.increment)`
2. Add the Phase 2 CHANGELOG entry at the top of docs/CHANGELOG.md exactly as in the plan (Cloud Function, Android wiring,
   firestore.rules, analysis-only divergence note).
3. Backend final: `npm --prefix functions test` (all suites pass) and `npm --prefix functions run build` (tsc clean).
4. Android final: `./gradlew :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL, all unit tests pass.
5. Commit: git add CLAUDE.md docs/CHANGELOG.md
   message: docs: update water schema (ml) and add Phase 2 changelog
   end body with: Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
6. Report [✓ הושלם] for code/tests with the full results.
7. STOP and hand the user the remaining MANUAL steps (do NOT perform them):
     - firebase deploy --only functions
     - Enable the Vertex AI API in GCP
     - Configure App Check (register app, Play Integrity, add debug token)
     - Confirm .firebaserc project id + Vertex service-account permissions
     - (Optional) run the Task 12 emulator instrumented test on a device.
Report: Status, backend test/build results, android test/build results, commit SHA, and the manual-steps list.
```

---

## After Task 17

Run a final full-branch code review (diff `2712595`..HEAD) and then use the `finishing-a-development-branch`
flow (merge / PR) — but only after the user has done the manual Firebase steps and confirmed the function works end-to-end.
