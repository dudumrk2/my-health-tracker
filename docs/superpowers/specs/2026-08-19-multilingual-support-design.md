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

### 2.1 String Resources
Extract hardcoded Hebrew strings into:
- `app/src/main/res/values/strings.xml` (Hebrew default strings)
- `app/src/main/res/values-en/strings.xml` (English translated strings)

Categories of strings to extract:
- General / Navigation labels (Dashboard, Food, Activity, Profile, Reminders, etc.)
- Dashboard metric cards, progress bars, insight cards, empty states
- Food / Meal screens (Add meal, photo, notes, item edits, nutrition summary, quality badges)
- Activity / Workout screens (Add workout, workout types, intensity, duration, steps)
- Profile / Settings screens (Personal details, goals, preferences, sound, theme, language selector, delete account)
- Reminders / Notifications (Reminder titles, quick action notification actions, water quick-log buttons)

### 2.2 Dynamic Locale and Direction
- In `MainActivity.kt`:
  When `profileData` emits a change in `language`:
  ```kotlin
  val appLocale = if (language == "en") "en" else "he"
  val currentLocales = AppCompatDelegate.getApplicationLocales()
  if (currentLocales.toLanguageTags() != appLocale) {
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLocale))
  }
  ```
- In `Theme.kt`:
  Set `LocalLayoutDirection` dynamically:
  ```kotlin
  val isRtl = (language == "he") // or based on Configuration/Locale
  val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
      content()
  }
  ```

### 2.3 Profile Screen Language Selector
Add a language selector in `ProfileScreen.kt` under "העדפות ממשק / Interface Preferences":
- Label: `שפת ממשק` / `App Language`
- Options: `עברית` (Hebrew), `English` (English)
- Segmented buttons / dropdown matching the existing `themePreference` selector pattern.

---

## 3. Cloud Functions & AI Prompts

### 3.1 `prompts.ts`
- **`ProfileContext`**: Add `language?: string`.
- **`buildMealSystemInstruction(profile: ProfileContext | null)`**:
  - Check `profile?.language === "en"`:
    - Instruct model: `Write the 'name' and 'quantity' fields in English. Keep all numeric values as plain numbers.`
    - `Quantity is a short human-readable string in English (e.g. '150g', '1 cup').`
    - `In the 'recommendation' field, provide a single, focused, actionable recommendation in English for adding an ingredient or side dish...`
  - When `profile?.language !== "en"` (default Hebrew):
    - Maintain existing Hebrew instructions.
- **`buildInsightsSystemInstruction(language: string = "he")`**:
  - When `language === "en"`:
    - Instruct model: `Write every sentence in English. Each field is exactly ONE short, focused sentence.`
    - Tailor safety guidelines in English (`Prefer suggestions ('you might consider') over commands ('you must')`).
  - When `language === "he"`:
    - Maintain existing Hebrew instructions.
- **`buildInsightsUserPrompt(day: DayData, language: string = "he")`**:
  - Tailor closing prompt directive: `Produce focused, supportive one-sentence insights per the schema, in English.` (or in Hebrew).

### 3.2 `insightsParse.ts`
- Add `DISCLAIMER_EN = "Insights are general information only and do not constitute medical or nutritional advice. For health decisions, consult a qualified professional."`
- Update `parseInsights(raw: string, language: string = "he"): ParsedInsights` to attach `DISCLAIMER_EN` or `DISCLAIMER_HE` based on `language`.

### 3.3 `fallback.ts`
- Update `buildFallbackInsights(day: DayData, language: string = "he"): ParsedInsights`:
  - When `language === "en"`:
    - Provide fallback strings in English for general, nutrition, activity, and sleep.
  - When `language === "he"`:
    - Use existing Hebrew fallback copy.

### 3.4 Integration in `analyzeMeal.ts` & `generateInsights.ts`
- `analyzeMeal.ts`: `readProfile` reads `language` from Firestore profile (`profile.language as string | undefined`) and includes it in `ProfileContext`.
- `generateInsights.ts`: `runInsightsForUser` passes user's `profile.language` down to prompt generators, parsers, and fallback generators.

---

## 4. Verification & Testing

### Automated Tests
1. **Unit Tests (Android / Kotlin):**
   - `ProfileRepositoryTest`: Test `mapProfile` parsing and serialization with `language = "en"` and default `"he"`.
   - String resource completeness test or verification that keys exist in both `values/strings.xml` and `values-en/strings.xml`.
2. **Unit Tests (Cloud Functions / TypeScript):**
   - `prompts.test.ts`: Verify `buildMealSystemInstruction`, `buildInsightsSystemInstruction`, and `buildInsightsUserPrompt` produce correct English / Hebrew instructions.
   - `insightsParse.test.ts`: Verify disclaimer selection for `"en"` and `"he"`.
   - `fallback.test.ts`: Verify English and Hebrew fallback outputs.

### Manual Verification
- Launch app, verify Hebrew layout & RTL.
- Change language to English in Profile Settings and save.
- Verify instant UI update to English, LTR layout direction, and all screens render in English.
- Log a meal or trigger insight refresh and verify generated output is in English.
