# Multilingual Support (Hebrew & English) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete bilingual support (Hebrew and English) to MyHealthTracker across Android UI, system locale switching, and Cloud Functions AI prompts/insights.

**Architecture:** 
- Store `language: "he" | "en"` in `UserProfile` in Firestore.
- Use Android standard localization (`res/values/strings.xml` for Hebrew default, `res/values-en/strings.xml` for English, and `AppCompatDelegate.setApplicationLocales` with `locale_config.xml`).
- Update `Theme.kt` to let system locale control `LayoutDirection` (RTL for Hebrew, LTR for English).
- Update Cloud Functions prompts (`prompts.ts`, `insightsParse.ts`, `fallback.ts`, `aggregate.ts`, `analyzeMeal.ts`, `generateInsights.ts`) to produce AI meals and insights in English or Hebrew based on the user's saved language preference.

**Tech Stack:** Kotlin, Jetpack Compose, Android `AppCompatDelegate`, Android String Resources, Cloud Functions (TypeScript), Vertex AI Gemini 2.5 Flash, Jest, JUnit 4, MockK.

---

### Task 1: Cloud Functions — Multilingual Prompts, Parser, Fallback, and Aggregation

**Files:**
- Modify: `functions/src/prompts.ts`
- Modify: `functions/src/insights/insightsParse.ts`
- Modify: `functions/src/insights/fallback.ts`
- Modify: `functions/src/insights/aggregate.ts`
- Modify: `functions/src/analyzeMeal.ts`
- Modify: `functions/src/generateInsights.ts`
- Test: `functions/test/prompt.test.ts`
- Test: `functions/test/insightsParse.test.ts`
- Test: `functions/test/fallback.test.ts`
- Test: `functions/test/aggregate.test.ts`

- [ ] **Step 1: Write failing unit tests for English/Hebrew prompt and parser generation**

In `functions/test/prompt.test.ts`, add tests for `buildMealSystemInstruction`, `buildInsightsSystemInstruction`, and `buildInsightsUserPrompt` with `language: "en"` vs `language: "he"`.
In `functions/test/insightsParse.test.ts`, add tests for `parseInsights` returning `DISCLAIMER_EN` when `language === "en"`.
In `functions/test/fallback.test.ts`, add tests for `buildFallbackInsights` returning English copy when `language === "en"`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npm test`
Expected: FAIL due to missing language parameters and assertions.

- [ ] **Step 3: Implement prompt, parser, fallback, and aggregate updates**

1. In `functions/src/prompts.ts`:
   - In `ProfileContext`: add `language?: string`.
   - In `buildMealSystemInstruction(profile: ProfileContext | null)`: if `profile?.language === "en"`, instruct English names, English quantity, and English recommendation. Otherwise default to Hebrew.
   - In `buildInsightsSystemInstruction(language: string = "he")`: if `language === "en"`, instruct English output and English tone guidelines. Otherwise default to Hebrew.
   - In `buildInsightsUserPrompt(day: DayData, language: string = "he")`: if `language === "en"`, request output in English.

2. In `functions/src/insights/insightsParse.ts`:
   - Export `DISCLAIMER_EN = "Insights are general information only and do not constitute medical or nutritional advice. For health decisions, consult a qualified professional."`
   - Update `parseInsights(raw: string, language: string = "he"): ParsedInsights` to use `language === "en" ? DISCLAIMER_EN : DISCLAIMER_HE`.

3. In `functions/src/insights/fallback.ts`:
   - Update `buildFallbackInsights(day: DayData, language: string = "he"): ParsedInsights` to generate English text when `language === "en"`.

4. In `functions/src/insights/aggregate.ts`:
   - In `DayProfile`: add `language?: string`.
   - In `fetchDayData`: read `profile.language` as `typeof p.language === "string" ? p.language : "he"`.

5. In `functions/src/analyzeMeal.ts`:
   - In `readProfile`: read `profile.language` and return `{ ..., language: typeof profile.language === "string" ? profile.language : undefined }`.

6. In `functions/src/generateInsights.ts`:
   - In `runInsightsForUser` / `prodDeps`: pass `day.profile?.language || "he"` to `buildInsightsSystemInstruction`, `buildInsightsUserPrompt`, `parseInsights`, and `buildFallbackInsights`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npm test`
Expected: PASS (all test suites passing).

- [ ] **Step 5: Commit Task 1**

```bash
git add functions/
git commit -m "feat(functions): support bilingual (Hebrew/English) meal analysis and daily insights"
```

---

### Task 2: Android Data Layer — Language Field in `UserProfile`

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/data/profile/ProfileRepository.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ProfileAndHealthUnitTest.kt`

- [ ] **Step 1: Write failing unit test in `ProfileAndHealthUnitTest.kt`**

Add tests:
- `testMapProfile_language()`: verifies `mapProfile(mapOf("language" to "en")).language == "en"`, and `mapProfile(emptyMap()).language == "he"`.
- `testProfileValidation_language()`: verifies `"he"` and `"en"` pass validation, and invalid values fail.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.myhealthtracker.app.ProfileAndHealthUnitTest"`
Expected: FAIL because `language` property does not exist on `UserProfile`.

- [ ] **Step 3: Update `UserProfile` and `FirestoreProfileRepository`**

1. In `ProfileRepository.kt`:
   - Add `val language: String = "he"` to `UserProfile`.
   - In `mapProfile`: read `language = (profileMap["language"] as? String) ?: "he"`.
   - In `saveUserProfile`: include `"language" to profile.language` in the profile map written to Firestore.
   - In `validateProfile`: ensure `profile.language in listOf("he", "en")`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.myhealthtracker.app.ProfileAndHealthUnitTest"`
Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/myhealthtracker/app/data/profile/ app/src/test/java/com/myhealthtracker/app/ProfileAndHealthUnitTest.kt
git commit -m "feat(data): add language preference to UserProfile and ProfileRepository"
```

---

### Task 3: Android Configuration & String Resources Extraction

**Files:**
- Create: `app/src/main/res/xml/locale_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Create `locale_config.xml`**

In `app/src/main/res/xml/locale_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="he"/>
    <locale android:name="en"/>
</locale-config>
```

- [ ] **Step 2: Update `AndroidManifest.xml`**

Add `android:localeConfig="@xml/locale_config"` to `<application>` in `app/src/main/AndroidManifest.xml`.

- [ ] **Step 3: Define complete string resources in `values/strings.xml` (Hebrew) and `values-en/strings.xml` (English)**

Include all common and screen-specific strings:
- Navigation: `nav_dashboard`, `nav_food`, `nav_activity`, `nav_profile`
- Common actions: `save`, `cancel`, `delete`, `edit`, `back`, `close`, `retry`, `confirm`
- Dashboard: titles, calories, protein, carbs, fat, water, steps, sleep, workouts, streak, insights section, goals
- Food / Meals: add meal, take photo, pick gallery, analyze, food items, portion, calories, macros, nutritional quality, badges, edit item, delete item, meal note placeholder
- Activity: add workout, steps goal, workout types (walking, running, cycling, strength, swimming, yoga, other), duration, intensity, sync
- Profile: personal details, first name, birth year, weight, height, gender (male/female), theme (system/dark/light), language (Hebrew/English), primary goal (lose/maintain/gain), activity level, focus areas, reminder settings button, logout, delete account
- Reminders & Notifications: reminder settings, morning/noon/evening meal reminders, hydration reminder, evening summary reminder, quick actions notification title/buttons

- [ ] **Step 4: Verify XML builds**

Run: `./gradlew processDebugResources`
Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/res/
git commit -m "feat(res): add locale config and bilingual Hebrew/English string resources"
```

---

### Task 4: Android Dynamic Locale Switching & Theme Layout Direction

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/theme/Theme.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/MainActivity.kt`

- [ ] **Step 1: Update `Theme.kt`**

Remove the hardcoded `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` block. Let Compose inherit the layout direction from the Android system configuration which is set by `AppCompatDelegate`.

- [ ] **Step 2: Update `MainActivity.kt`**

In `MainActivity.kt`, add a `LaunchedEffect(profileData)` watching `profileData.getOrNull()?.language`:
```kotlin
val language = profileData.getOrNull()?.language ?: "he"
LaunchedEffect(language) {
    val tag = if (language == "en") "en" else "he"
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (current != tag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: PASS.

- [ ] **Step 4: Commit Task 4**

```bash
git add app/src/main/java/com/myhealthtracker/app/theme/Theme.kt app/src/main/java/com/myhealthtracker/app/MainActivity.kt
git commit -m "feat(ui): dynamic runtime locale switching and automatic layout direction"
```

---

### Task 5: Profile Screen Language Selector & ViewModel

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: Update `ProfileViewModel.kt`**

- In `ProfileUiState`: add `language: String = "he"`.
- In `loadProfile`: populate `language = profile.language`.
- In `saveProfile`: pass `language` into `UserProfile`.

- [ ] **Step 2: Update `ProfileScreen.kt`**

- Bind `var selectedLanguage by remember { mutableStateOf("he") }` from `uiState.language`.
- Under "העדפות ממשק / Interface Preferences", add a Segmented Button Row / Selector for `שפת ממשק / App Language` with options:
  - `עברית` (`"he"`)
  - `English` (`"en"`)
- Migrate strings in `ProfileScreen.kt` to `stringResource(...)`.

- [ ] **Step 3: Run ProfileViewModel tests**

Run: `./gradlew testDebugUnitTest --tests "com.myhealthtracker.app.ui.profile.*"`
Expected: PASS.

- [ ] **Step 4: Commit Task 5**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/
git commit -m "feat(profile): add language selector in profile screen"
```

---

### Task 6: Migrate Screens & Notifications to String Resources

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/activity/ActivityScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealResultContent.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/MealEditScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/workout/AddWorkoutScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/reminders/ReminderSettingsScreen.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/notification/QuickActionsNotificationManager.kt`
- Modify: `app/src/main/java/com/myhealthtracker/app/notification/ReminderScheduler.kt`

- [ ] **Step 1: Migrate UI Composables to `stringResource(R.string.xxx)`**

Replace hardcoded UI labels with corresponding `stringResource(R.string.xxx)` calls across:
- `MainScreen.kt` (Navigation bar items)
- `DashboardScreen.kt` (Header, metrics, insights card, quick actions)
- `FoodScreen.kt` & `AddMealScreen.kt` & `MealResultContent.kt` & `MealEditScreen.kt` (Meal logging, macro bars, item list, nutritional quality)
- `ActivityScreen.kt` & `AddWorkoutScreen.kt` (Workout list, workout types, stats)
- `ReminderSettingsScreen.kt` (Reminder toggles and time pickers)

- [ ] **Step 2: Migrate Notifications to `context.getString(R.string.xxx)`**

Ensure notification channels, titles, content texts, and actions in `QuickActionsNotificationManager.kt` and `ReminderScheduler.kt` use `context.getString(...)`.

- [ ] **Step 3: Run all Android unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit Task 6**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/ app/src/main/java/com/myhealthtracker/app/notification/
git commit -m "feat(ui): migrate hardcoded UI and notification strings to string resources"
```

---

### Task 7: End-to-End Verification & Production Build

**Files:**
- Verification only

- [ ] **Step 1: Run all Cloud Functions unit tests**

Run: `cd functions && npm test`
Expected: All 15+ test suites PASS with 0 failures.

- [ ] **Step 2: Run all Android unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All unit tests PASS with 0 failures.

- [ ] **Step 3: Run Android assembleDebug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit any final cleanup and verify git status**

```bash
git status
```
