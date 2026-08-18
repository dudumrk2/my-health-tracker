# Settings Page Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the profile/settings screen: reorder display+notifications to the bottom, replace the manual-override block with a per-field pencil-edit dialog, clarify the calories row, and show cached values instantly on open.

**Architecture:** All changes are in the Compose UI layer (`ProfileScreen.kt`) plus one small pure formatting helper (`GoalFormat.kt`). No data-model, repository, or `GoalCalculator` change — the per-field dialog edits the same `goalOverrides` form state the screen already holds, and Firestore's existing Android offline cache supplies instant values.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit unit tests, Gradle.

## Global Constraints

- Kotlin, Jetpack Compose + MVVM; no blocking calls on main thread.
- User-facing strings are Hebrew (RTL); code/comments/identifiers English only.
- Source of truth stays Firestore (cloud-first decision) — no new local-storage layer (DataStore/SharedPreferences); reuse Firestore's offline cache.
- No change to `GoalOverrides` / `UserProfile` data classes, Firestore schema, or `GoalCalculator` formulas.
- Overridable goals are exactly: `caloriesKcal`, `steps`, `proteinG`, `waterMl`, `sleepHours`. `fatG`/`carbsG` are read-only (recomputed).
- Tests run with `./gradlew :app:testDebugUnitTest`; build check with `./gradlew assembleDebug`.
- `Icons.Default.Edit` is available (already used in `AddMealScreen.kt`).

---

## File Structure

- **Create** `app/src/main/java/com/myhealthtracker/app/ui/profile/GoalFormat.kt` — pure display helpers for the goals list (sleep range formatting). Kept out of the Compose file so it is unit-testable without a Compose runtime.
- **Create** `app/src/test/java/com/myhealthtracker/app/ui/profile/GoalFormatTest.kt` — unit tests for the helpers.
- **Modify** `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt` — goals redesign (pencil dialog, calories caption), section reorder, instant-display fix.

The dialog, reorder, and instant-display changes are all in `ProfileScreen.kt` but are split into separate tasks because a reviewer could accept one and reject another.

---

## Task 1: Sleep-range formatting helper (TDD)

**Files:**
- Create: `app/src/main/java/com/myhealthtracker/app/ui/profile/GoalFormat.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/profile/GoalFormatTest.kt`

**Interfaces:**
- Produces: `fun formatSleepGoal(min: Int, max: Int): String` — returns `"$min שעות"` when `min == max`, otherwise `"$min-$max שעות"`. Used by the sleep `GoalLine` in Task 2.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/myhealthtracker/app/ui/profile/GoalFormatTest.kt`:

```kotlin
package com.myhealthtracker.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalFormatTest {

    @Test
    fun rangeWhenMinDiffersFromMax() {
        assertEquals("7-9 שעות", formatSleepGoal(7, 9))
    }

    @Test
    fun singleValueWhenMinEqualsMax() {
        // After a sleep override, GoalCalculator sets min == max.
        assertEquals("8 שעות", formatSleepGoal(8, 8))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.profile.GoalFormatTest"`
Expected: FAIL to compile — `formatSleepGoal` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/myhealthtracker/app/ui/profile/GoalFormat.kt`:

```kotlin
package com.myhealthtracker.app.ui.profile

/**
 * Formats the sleep goal for display. After a manual sleep override GoalCalculator sets
 * min == max, so we show a single value instead of a "7-7" range.
 */
fun formatSleepGoal(min: Int, max: Int): String =
    if (min == max) "$min שעות" else "$min-$max שעות"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.profile.GoalFormatTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/GoalFormat.kt app/src/test/java/com/myhealthtracker/app/ui/profile/GoalFormatTest.kt
git commit -m "feat: add sleep-goal display formatter"
```

---

## Task 2: Goals section redesign — pencil dialog + calories caption

Replaces the "דריסה ידנית" block and the inline `(TDEE …)` suffix with per-field pencil editing and a clearer calories row. This is a Compose-UI change; verification is build success + the focused manual check listed in Step 6 (the override save path itself is unchanged and already covered by `GoalCalculatorTest`).

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `formatSleepGoal(min, max)` from Task 1; existing form state vars `caloriesOverride`, `stepsOverride`, `proteinOverride`, `waterOverride`, `sleepOverride` and their `on…OverrideChange` callbacks (already passed into `ProfileScreenContent`); `goals: HealthGoals`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Add imports**

At the top of `ProfileScreen.kt`, add (next to the existing imports):

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.KeyboardOptions
```

(`KeyboardOptions` is already imported — only add the two icon imports if `KeyboardOptions` already present.)

- [ ] **Step 2: Define the editable-goal model and dialog composable**

Add near the other private composables (e.g. just above `GoalLine`):

```kotlin
/** Goals that map to a GoalOverrides field and can be edited via the pencil dialog. */
private enum class EditableGoal(val title: String) {
    CALORIES("עריכת יעד קלוריות"),
    PROTEIN("עריכת יעד חלבון (ג)"),
    STEPS("עריכת יעד צעדים"),
    SLEEP("עריכת יעד שינה (שעות)"),
    WATER("עריכת יעד מים (מ\"ל)")
}

/**
 * Edit dialog for a single goal. [initialValue] pre-fills the field with the current effective
 * value; [onApply] sets the override (empty string clears it), [onReset] clears it to the
 * computed value. Both close the dialog via [onDismiss].
 */
@Composable
private fun GoalEditDialog(
    goal: EditableGoal,
    initialValue: String,
    onApply: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(goal) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(goal.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                TextButton(onClick = { onReset(); onDismiss() }) {
                    Text("אפס לערך המחושב")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft); onDismiss() }) { Text("שמירה") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}
```

- [ ] **Step 3: Extend `GoalLine` to support a caption and an edit pencil**

Replace the existing `GoalLine` composable (currently lines ~652-665) with:

```kotlin
@Composable
private fun GoalLine(
    label: String,
    value: String,
    caption: String? = null,
    onEdit: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "עריכה",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 4: Add dialog state and rewrite the goals card body**

In `ProfileScreenContent`, add dialog state next to the existing `showDeleteDialog` declaration (~line 257):

```kotlin
var editingGoal by remember { mutableStateOf<EditableGoal?>(null) }
```

Then replace the goals-card body. The current block (lines ~493-529) runs from `FieldLabel("היעדים שלך")` through the closing disclaimer `Text`. Replace the `GoalLine(...)` rows, the `Spacer`/`FieldLabel("דריסה ידנית …")` block and all five `OverrideField(...)` calls with:

```kotlin
                    FieldLabel("היעדים שלך")
                    if (goals.isGeneric) {
                        Text(
                            text = "יעד כללי עד להשלמת פרטי הפרופיל.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (goals.extremeAdjustmentWarning) {
                        Text(
                            text = "⚠ יעד הקלוריות שהוגדר חורג מ-35% מההוצאה היומית המוערכת. כדאי לשקול יעד מתון יותר.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    GoalLine(
                        label = "קלוריות",
                        value = "${goals.caloriesKcal} קק\"ל",
                        caption = if (goals.tdee > 0) "הוצאה יומית מוערכת (TDEE): ${goals.tdee} קק\"ל" else null,
                        onEdit = { editingGoal = EditableGoal.CALORIES }
                    )
                    GoalLine("חלבון", "${goals.proteinG} ג", onEdit = { editingGoal = EditableGoal.PROTEIN })
                    GoalLine("שומן", "${goals.fatG} ג")
                    GoalLine("פחמימות", "${goals.carbsG} ג")
                    GoalLine("צעדים", "${goals.steps}", onEdit = { editingGoal = EditableGoal.STEPS })
                    GoalLine("שינה", formatSleepGoal(goals.sleepHoursMin, goals.sleepHoursMax), onEdit = { editingGoal = EditableGoal.SLEEP })
                    GoalLine("מים", "${goals.waterMl} מ\"ל", onEdit = { editingGoal = EditableGoal.WATER })

                    Text(
                        text = HEALTH_DISCLAIMER_HE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
```

- [ ] **Step 5: Render the dialog**

Inside `ProfileScreenContent`, just before the closing of the outer `Column` (next to where `showDeleteDialog` dialog is rendered, ~line 618), add:

```kotlin
            editingGoal?.let { goal ->
                val current = when (goal) {
                    EditableGoal.CALORIES -> caloriesOverride.ifBlank { goals.caloriesKcal.toString() }
                    EditableGoal.PROTEIN -> proteinOverride.ifBlank { goals.proteinG.toString() }
                    EditableGoal.STEPS -> stepsOverride.ifBlank { goals.steps.toString() }
                    EditableGoal.SLEEP -> sleepOverride.ifBlank { goals.sleepHoursMin.toString() }
                    EditableGoal.WATER -> waterOverride.ifBlank { goals.waterMl.toString() }
                }
                val onApply: (String) -> Unit = { v ->
                    when (goal) {
                        EditableGoal.CALORIES -> onCaloriesOverrideChange(v)
                        EditableGoal.PROTEIN -> onProteinOverrideChange(v)
                        EditableGoal.STEPS -> onStepsOverrideChange(v)
                        EditableGoal.SLEEP -> onSleepOverrideChange(v)
                        EditableGoal.WATER -> onWaterOverrideChange(v)
                    }
                }
                GoalEditDialog(
                    goal = goal,
                    initialValue = current,
                    onApply = onApply,
                    onReset = { onApply("") },
                    onDismiss = { editingGoal = null }
                )
            }
```

- [ ] **Step 6: Remove the now-unused `OverrideField` composable**

Delete the `OverrideField` composable (currently lines ~667-678). Confirm there are no remaining references:

Run: `git grep -n "OverrideField" app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`
Expected: no output.

- [ ] **Step 7: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual check (emulator/device): open the settings screen → the "היעדים שלך" card shows the calories row with a `(TDEE …)` caption beneath it and a pencil; tapping a pencil opens a dialog pre-filled with the current value; entering a value and "שמירה" updates the row live; "אפס לערך המחושב" returns it to the computed value; the old "דריסה ידנית" block is gone.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt
git commit -m "feat: per-field goal editing dialog and clearer calories row"
```

---

## Task 3: Move display preference + notifications above Account

Lift the theme `SelectRow` and the quick-actions notification `Row`/`Switch` out of the Basic-details card into a new "תצוגה והתראות" card placed directly above the Account card.

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: existing `themePreference` + `onThemeSelect`, and the existing `quickActionsEnabled` + `onQuickActionsEnabledChange` plumbing already passed into `ProfileScreenContent`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Remove the theme + notifications block from the Basic-details card**

In the Basic-details card, delete the theme `Column { FieldLabel("העדפת תצוגה") … }` block, the `HorizontalDivider` that follows it, and the entire notifications block — i.e. the `LocalContext`/`permissionLauncher` declarations and the `Row { … Switch(…) }` (currently lines ~368-424). The card then ends after the height `OutlinedTextField`.

- [ ] **Step 2: Add the new "תצוגה והתראות" card directly above the Account card**

Immediately before the `// ── Account: logout + delete ──` card (~line 568), insert:

```kotlin
            // ── Display preference + quick-action notifications ───────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        FieldLabel("העדפת תצוגה")
                        SelectRow(
                            options = listOf("system" to "מערכת", "light" to "בהירה", "dark" to "כהה"),
                            selectedValue = themePreference,
                            onSelect = onThemeSelect
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    val context = LocalContext.current
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        onQuickActionsEnabledChange(isGranted)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "התראת פעולות מהירות",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "הצגת התראה קבועה להוספה מהירה של ארוחה, אימון ומים",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = quickActionsEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            onQuickActionsEnabledChange(true)
                                        } else {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        onQuickActionsEnabledChange(true)
                                    }
                                } else {
                                    onQuickActionsEnabledChange(false)
                                }
                            }
                        )
                    }
                }
            }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual check: Basic-details card now ends at the height field (no theme/notification rows). A new "תצוגה והתראות" card appears just above the "חשבון" card with the theme selector and the notification toggle, both still functional. The מטרת שימוש / רמת פעילות card is unchanged in its original position.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt
git commit -m "feat: move display preference and notifications above account section"
```

---

## Task 4: Instant display from cache (remove initial-load spinner flash)

The form's cards/fields are already always composed and are populated from `ProfileUiState.Loaded` (which Firestore delivers from its on-device cache near-instantly). The only thing that currently makes the page look like it is "loading" on open is that the bottom Save button is swapped for a `CircularProgressIndicator` whenever `uiState is Loading` — and the initial profile load uses that same `Loading` state. This task scopes that spinner to in-flight saves only, so cached values render immediately with the Save button present.

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: existing `uiState: ProfileUiState`, `onSaveClick`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Track save-in-flight locally**

In `ProfileScreenContent`, next to the `editingGoal` / `showDeleteDialog` state, add:

```kotlin
var isSaving by remember { mutableStateOf(false) }
```

Add a reset effect so the flag clears once a save resolves (or any non-Loading state is reached):

```kotlin
LaunchedEffect(uiState) {
    if (uiState !is ProfileUiState.Loading) isSaving = false
}
```

- [ ] **Step 2: Scope the bottom spinner to saving only**

Replace the bottom save/loading block (currently `if (uiState is ProfileUiState.Loading) { CircularProgressIndicator(...) } else { Button(onClick = onSaveClick, ...) { ... } }`, lines ~543-566) with a version that shows the spinner only while saving and wraps `onSaveClick` to set the flag:

```kotlin
            if (isSaving) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = {
                        isSaving = true
                        onSaveClick()
                    },
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "סיום",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
```

Note: on a validation error the ViewModel emits `ProfileUiState.Error`; the `LaunchedEffect` above resets `isSaving` so the Save button returns. The existing `uiState is ProfileUiState.Error` message block (just above) is unchanged.

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual check (app must have a previously-saved profile): cold-open the settings screen → all fields and goals show their saved values immediately, the Save ("סיום") button is visible (no spinner) during the initial load; pressing "סיום" shows the spinner while saving; a validation error restores the button.

- [ ] **Step 4: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all existing tests + Task 1's `GoalFormatTest` pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt
git commit -m "fix: show cached profile instantly on open, scope spinner to saving"
```

---

## Self-Review

**Spec coverage:**
- §1 reorder (display + notifications only, goal/activity card stays) → Task 3.
- §2.1 per-field pencil dialog, remove דריסה ידנית block → Task 2.
- §2.2 clearer calories row (caption instead of inline TDEE) → Task 2.
- §2.3 sleep min==max formatting → Task 1 + used in Task 2.
- §3 instant display from cache, no blank flash → Task 4.
- "No data-model / GoalCalculator change" → respected (Global Constraints); override save path reused unchanged.

**Placeholder scan:** No TBD/TODO; every code step shows full code; commands have expected output.

**Type consistency:** `formatSleepGoal(min, max)` defined in Task 1, used in Task 2. `EditableGoal` enum defined and consumed within Task 2. `editingGoal`/`isSaving` state names consistent across Tasks 2 and 4. Override callback names (`onCaloriesOverrideChange`, `onProteinOverrideChange`, `onStepsOverrideChange`, `onSleepOverrideChange`, `onWaterOverrideChange`) match the existing `ProfileScreenContent` signature.
