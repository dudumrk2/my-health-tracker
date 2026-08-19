# Design Document: Multilingual Support (Hebrew & English)

## Overview
Add bilingual support (Hebrew & English) across MyHealthTracker:
1. Allow users to select their preferred language (`he` for Hebrew, `en` for English) in Settings / Profile.
2. Store language preference in `UserProfile` in Firestore.
3. Automatically update the Android app UI language, strings, and direction (`RTL` vs `LTR`) using Android's standard Localization system (`res/values/strings.xml`, `res/values-en/strings.xml`, `AppCompatDelegate.setApplicationLocales`, and dynamic `LocalLayoutDirection`).
4. Update Cloud Functions AI prompts (`analyzeMeal`, `generateInsights`, fallback insights, disclaimers) to respect the user's selected language.

---

## 1. Data Model & Firestore

### 1.1 Android `UserProfile` Model
Update `UserProfile` in `app/src/main/java/com/myhealthtracker/app/data/profile/ProfileRepository.kt`:
```kotlin
data class UserProfile(
    val firstName: String = "",
    val birthYear: Int = 0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val gender: String = "",
    val themePreference: String = "system",
    val language: String = "he", // "he" | "en"
    val primaryGoal: String = "maintain",
    val activityLevel: String = "moderate",
    val focusAreas: List<String> = emptyList(),
    val goalOverrides: GoalOverrides? = null,
    val quickActionsEnabled: Boolean = true,
    val celebrationSoundEnabled: Boolean = true,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)
```

### 1.2 Serialization / Deserialization
- In `mapProfile(profileMap: Map<*, *>)`:
  `language = (profileMap["language"] as? String) ?: "he"`
- In `ProfileRepositoryImpl.saveUserProfile`:
  Include `"language" to profile.language` in the Firestore document map.

---

## 2. Android UI & Localization

### 2.1 String Resources — Scope
Extract **user-visible hardcoded Hebrew strings** into:
- `app/src/main/res/values/strings.xml` (Hebrew default strings)
- `app/src/main/res/values-en/strings.xml` (English translated strings)

**In scope** — strings shown in the UI:
- General / Navigation labels (Dashboard, Food, Activity, Profile, Reminders, etc.)
- Dashboard metric cards, progress bars, insight cards, empty states
- Food / Meal screens (Add meal, photo, notes, item edits, nutrition summary, quality badges)
- Activity / Workout screens (Add workout, workout types, intensity, duration, steps)
- Profile / Settings screens (Personal details, goals, preferences, sound, theme, language selector, delete account)
- Reminders / Notifications (Reminder titles, quick action notification actions, water quick-log buttons)

**Out of scope** — internal strings (never extracted):
- Firestore field keys / enum values (e.g. `"lose"`, `"maintain"`, `"gain"`)
- Log messages and analytics event names
- AI prompt copy in Cloud Functions (handled separately in Section 3)

### 2.2 Dynamic Locale and Direction
Use **`AppCompatDelegate.setApplicationLocales`** exclusively. This API:
- Replaces the app locale at runtime without restarting the process on API 33+
- Falls back gracefully to a full Activity restart on API 28–32
- Automatically sets `LocalLayoutDirection` for Compose (RTL for `he`, LTR for `en`) — **no manual `LocalLayoutDirection` override in Theme.kt is needed or wanted.**

In `MainActivity.kt`, react to `profileData.language` changes:
```kotlin
LaunchedEffect(language) {
    val tag = if (language == "en") "en" else "he"
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (current != tag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
```

**Remove** the existing `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` block from `Theme.kt` so that the system-managed locale controls layout direction.

### 2.3 Manifest — `locale_config.xml`
`AppCompatDelegate.setApplicationLocales` requires an explicit locale config:

**New file** `app/src/main/res/xml/locale_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="he"/>
    <locale android:name="en"/>
</locale-config>
```

**Add to `AndroidManifest.xml`** `<application>` element:
```xml
android:localeConfig="@xml/locale_config"
```

### 2.4 Profile Screen Language Selector
Add a language selector in `ProfileScreen.kt` under the existing "preferences" section (same area as `themePreference`):
- Label: `שפת ממשק` (Hebrew mode) / `App Language` (English mode) — use the language-aware string resource.
- Options: `עברית` (Hebrew), `English` (English).
- Pattern: segmented button row matching the existing `themePreference` selector.
- On selection: call `viewModel.setLanguage(newLanguage)` → saves to Firestore → `MainActivity` `LaunchedEffect` fires `setApplicationLocales`.

---

## 3. Cloud Functions & AI Prompts

### 3.1 `prompts.ts`
- **`ProfileContext`**: Add `language?: string`.
- **`buildMealSystemInstruction(profile: ProfileContext | null)`**:
  - When `profile?.language === "en"`:
    - `Write the 'name' and 'quantity' fields in English. Keep all numeric values as plain numbers.`
    - `Quantity is a short human-readable string in English (e.g. '150g', '1 cup').`
    - `In the 'recommendation' field, provide a single, focused, actionable recommendation in English...`
  - When `profile?.language !== "en"` (default Hebrew): maintain existing Hebrew instructions unchanged.
- **`buildInsightsSystemInstruction(language: string = "he")`**:
  - When `language === "en"`:
    - `Write every sentence in English. Each field is exactly ONE short, focused sentence.`
    - Tailor safety guidelines in English (`Prefer suggestions ('you might consider') over commands ('you must')`).
  - When `language === "he"`: maintain existing Hebrew instructions unchanged.
- **`buildInsightsUserPrompt(day: DayData, language: string = "he")`**:
  - Closing directive: `Produce focused, supportive one-sentence insights per the schema, in English.` (or in Hebrew).

### 3.2 `insights/aggregate.ts` — Expose `language` in `DayData`
`DayData.profile` already carries `primaryGoal`, `focusAreas`, etc. Add `language?: string` to the profile sub-object so that the scheduled (`runForAllUsers`) and on-demand (`generateInsightsTrigger`) paths both have access to the user's language without extra Firestore reads.

### 3.3 `insightsParse.ts`
- Add:
  ```ts
  export const DISCLAIMER_EN =
    "Insights are general information only and do not constitute medical or nutritional advice. For health decisions, consult a qualified professional.";
  ```
- Update signature: `parseInsights(raw: string, language: string = "he"): ParsedInsights`
  — attaches `DISCLAIMER_EN` or `DISCLAIMER_HE` based on `language`.

### 3.4 `fallback.ts`
- Update signature: `buildFallbackInsights(day: DayData, language: string = "he"): ParsedInsights`
  - When `language === "en"`: English fallback copy for general, nutrition, activity, sleep, and disclaimer.
  - When `language === "he"`: existing Hebrew copy unchanged.

### 3.5 Integration — `analyzeMeal.ts` & `generateInsights.ts`
- `analyzeMeal.ts` → `readProfile`: also read `language` from Firestore → include in `ProfileContext`.
- `generateInsights.ts` → `runInsightsForUser`: `DayData.profile.language` flows through to all prompt builders, `parseInsights`, and `buildFallbackInsights`. No extra Firestore read needed.

---

## 4. Verification & Testing

### Automated Tests
1. **Unit Tests (Android / Kotlin):**
   - `ProfileRepositoryTest`: `mapProfile` with `language = "en"` and missing-key fallback to `"he"`.
   - `ProfileRepositoryTest`: `saveUserProfile` serializes `language` field.
2. **Unit Tests (Cloud Functions / TypeScript):**
   - `prompts.test.ts`: `buildMealSystemInstruction`, `buildInsightsSystemInstruction`, `buildInsightsUserPrompt` — verify English and Hebrew branches.
   - `insightsParse.test.ts`: disclaimer selection for `"en"` and `"he"`.
   - `fallback.test.ts`: English and Hebrew fallback copy.

### Manual Verification
1. Launch app → Hebrew layout (RTL), all strings in Hebrew.
2. In Profile → change language to English → save.
3. App switches to English, LTR layout, all visible strings in English.
4. Log a meal → verify AI-generated item names, quantities, and recommendation are in English.
5. Trigger insight refresh → verify all insight fields and disclaimer are in English.
6. Switch back to Hebrew → verify everything reverts.
