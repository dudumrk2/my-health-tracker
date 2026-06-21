# Settings Page Improvements — Design

**Date:** 2026-06-21
**Screen:** `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`
**Status:** Approved

## Goal

Improve the profile/settings screen on four points raised by the user:

1. Reorder sections so display preference and the notifications toggle sit near the
   bottom, just above the Account section.
2. Present the "היעדים שלך" (your goals) section more clearly.
   1. Replace the standalone "דריסה ידנית" (manual override) block with a per-field
      pencil that edits a single metric.
   2. Fix the unclear calories row.
   3. Show saved values immediately when the page opens (no blank/loading flash).

No data-model or Firestore-schema change is required. `GoalOverrides` already holds
exactly the five overridable fields (`caloriesKcal`, `steps`, `proteinG`, `waterMl`,
`sleepHours`).

## 1. Section reorder

Decision (confirmed with user): keep the מטרת שימוש / רמת פעילות card where it is; move
only the theme preference and the notifications toggle.

New top-to-bottom order:

1. Basic details — birth year, gender, weight, height. **Theme dropdown and the
   quick-actions notification toggle are removed from this card.**
2. מטרת שימוש + רמת פעילות (unchanged position — it drives the goal calculation).
3. היעדים שלך (redesigned — section 2 below).
4. תחומים שחשובים לי (focus areas, unchanged).
5. **תצוגה והתראות** — new card holding העדפת תצוגה (theme `SelectRow`) and the
   quick-actions notification `Row` + `Switch`, lifted verbatim out of the Basic-details
   card. Placed directly above Account.
6. חשבון (account, unchanged).

The Save button keeps its current position (after the goals/focus cards, before
Account), matching today's layout.

## 2. "היעדים שלך" redesign

### 2.1 Per-field editing (replaces the "דריסה ידנית" block)

- Delete the `FieldLabel("דריסה ידנית (אופציונלי)")` block and all five `OverrideField`
  calls. The `OverrideField` composable is removed.
- Each goal row that maps to a `GoalOverrides` field gets a trailing pencil
  (`Icons.Default.Edit`) icon button: **קלוריות, חלבון, צעדים, שינה, מים**.
- חלבון-derived **שומן** and **פחמימות** rows stay read-only (no pencil); they recompute
  from calories/protein automatically via `GoalCalculator`.
- Tapping a pencil opens an `AlertDialog`:
  - Title: the metric name (e.g. "עריכת יעד קלוריות").
  - A single numeric `OutlinedTextField` pre-filled with the **current effective value**
    for that metric (the override if set, otherwise the computed value shown in the row).
  - A text action "אפס לערך המחושב" (reset to calculated) that clears this field's
    override.
  - Buttons: ביטול (dismiss, no change) / שמירה (apply).
- "שמירה" sets that single override string in the existing form state
  (`caloriesOverride`, `stepsOverride`, …). "אפס" sets it to `""`. The live preview
  (`goals = viewModel.previewGoals(... buildOverrides())`) updates immediately, exactly
  as the old inline fields did.
- Persisting to Firestore is unchanged: `buildOverrides()` and `onSaveClick` already read
  these same state variables.

Dialog state lives in `ProfileScreenContent` as a nullable "which metric is being edited"
value (e.g. an enum `EditableGoal { CALORIES, PROTEIN, STEPS, SLEEP, WATER }` plus the
draft text), so only one dialog is open at a time and the existing override callbacks
(`onCaloriesOverrideChange`, …) are reused unchanged.

### 2.2 Calories row

- Drop the inline `(TDEE 2528)` suffix from the calories value.
- Calories row shows: label `קלוריות`, value `2528 קק"ל`, pencil.
- Directly beneath it, a small muted caption shown only when `goals.tdee > 0`:
  `הוצאה יומית מוערכת (TDEE): 2528 קק"ל`.
- This is implemented by extending `GoalLine` (or adding a sibling) to accept an optional
  caption and an optional `onEdit` lambda. When `onEdit` is null no pencil renders; when
  caption is null no caption renders.

### 2.3 Sleep row formatting

- When `sleepHoursMin == sleepHoursMax` (the case after a sleep override), show
  `X שעות`. Otherwise show `X-Y שעות` as today.

## 3. Instant display on open (2.3)

Source of truth stays Firestore (consistent with the cloud-first decision). Firestore's
Android offline cache already persists the profile on-device, so cached values are
available synchronously on the first snapshot.

Change: stop gating the settings form behind the `ProfileUiState.Loading` state so the
cached profile renders immediately without a blank flash.

- The form's input state is already populated from `ProfileUiState.Loaded` via
  `LaunchedEffect`. The fix is to ensure the form (cards + fields) is always composed and
  populated as soon as `Loaded` arrives from cache, rather than showing a spinner or empty
  fields first.
- Concretely: the top-level `Loading` spinner (if it blanks the form) is replaced by
  keeping the form visible; only the Save button area reflects in-flight save/loading.
  Account-deletion progress UI is unaffected.
- No `FirebaseFirestoreSettings` / `setPersistenceEnabled` change is needed — offline
  persistence is on by default on Android. (If verification shows it is somehow disabled,
  enabling it explicitly is the fallback; not expected.)

## Testing

- ViewModel/screen tests: editing a single goal via the dialog updates the preview;
  "אפס לערך המחושב" clears that override; saving writes the expected `GoalOverrides`.
- Sleep formatting: `min == max` renders `X שעות`; `min != max` renders `X-Y שעות`.
- Calories caption: present when `tdee > 0`, absent when `tdee == 0` (generic goals).
- Existing `ProfileViewModel` / `ProfileScreen` tests continue to pass (override save path
  is unchanged).

## Out of scope

- No change to `GoalOverrides` / `UserProfile` data classes or Firestore schema.
- No new local-storage layer (DataStore/SharedPreferences); Firestore cache is reused.
- No change to `GoalCalculator` formulas.
