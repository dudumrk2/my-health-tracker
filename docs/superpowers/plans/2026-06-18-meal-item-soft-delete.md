# Meal Item Soft-Delete + Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user mark analyzed meal components as removed (strikethrough + restore) so totals recompute, without a confirmation dialog and without hard-deleting until save.

**Architecture:** The `AddMealViewModel` becomes the single source of truth: it keeps the full `recognizedItems` list plus a new `excludedIndices` set. Removing an item toggles its index in the set; `totals` and the saved meal are derived from the non-excluded ("active") items. The Compose `ResultStateContent` drops its duplicate local `itemsList` copy and renders strikethrough/dim for excluded items, disabling Save when all are excluded.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Coroutines/Flow, JUnit4 + kotlinx-coroutines-test.

## Global Constraints

- Kotlin, MVVM, Coroutines + Flow; no blocking calls on the main thread.
- User-facing strings are in Hebrew.
- `totals` must remain a **derived** value (computed via `sumOf`), never stored as independent state.
- Tests must pass before a phase is considered complete (`./gradlew test`).
- English only for code/identifiers/comments; Hebrew only for user-facing strings.

---

### Task 1: ViewModel soft-delete state and derived save

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`
- Test: `app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

**Interfaces:**
- Consumes: existing `_recognizedItems: MutableStateFlow<List<MealItem>>`, `MealItem`, `MealTotals`, the `FakeMealRepo`/`FakeAnalyzer` test fakes already in `AddMealViewModelTest.kt`.
- Produces (relied on by Task 2):
  - `val excludedIndices: StateFlow<Set<Int>>`
  - `fun toggleItemRemoved(index: Int)` — toggles membership; no-op if `index` is outside `recognizedItems.indices`.
  - `fun addItem(item: MealItem): Int` — appends to `recognizedItems`, returns the new item's index (`size - 1`).
  - `saveMeal()` now saves only items whose index is **not** in `excludedIndices`; if no active items remain it sets `errorMessage = "כל הפריטים הוסרו"` and does not save.

- [ ] **Step 1: Write the failing tests**

Add these tests to `AddMealViewModelTest.kt` (inside the existing class, after the last test). A small helper sets the VM into `ResultState` with a known item list:

```kotlin
private fun vmInResultState(
    repo: FakeMealRepo = FakeMealRepo(),
    items: List<MealItem>
): AddMealViewModel {
    val analyzer = FakeAnalyzer(
        result = MealAnalysisResult(
            items = items,
            totals = MealTotals(
                items.sumOf { it.calories },
                items.sumOf { it.proteinG },
                items.sumOf { it.carbsG },
                items.sumOf { it.fatG }
            ),
            lowConfidence = false
        )
    )
    val vm = AddMealViewModel(repo, analyzer)
    vm.onDescriptionChange("meal")
    vm.analyzeText()
    return vm
}

@Test
fun `toggleItemRemoved adds then removes index`() = runTest(dispatcher) {
    val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
    advanceUntilIdle()
    vm.toggleItemRemoved(0)
    assertEquals(setOf(0), vm.excludedIndices.value)
    vm.toggleItemRemoved(0)
    assertEquals(emptySet<Int>(), vm.excludedIndices.value)
}

@Test
fun `toggleItemRemoved ignores out-of-range index`() = runTest(dispatcher) {
    val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
    advanceUntilIdle()
    vm.toggleItemRemoved(5)
    assertEquals(emptySet<Int>(), vm.excludedIndices.value)
}

@Test
fun `addItem appends and returns new index`() = runTest(dispatcher) {
    val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
    advanceUntilIdle()
    val newIndex = vm.addItem(MealItem("B", "1", 50, 0, 0, 0))
    assertEquals(1, newIndex)
    assertEquals(2, vm.recognizedItems.value.size)
    assertEquals("B", vm.recognizedItems.value[1].name)
}

@Test
fun `saveMeal skips excluded items and recomputes totals`() = runTest(dispatcher) {
    val repo = FakeMealRepo()
    val vm = vmInResultState(
        repo = repo,
        items = listOf(
            MealItem("A", "1", 100, 10, 5, 2),
            MealItem("B", "1", 250, 20, 30, 8)
        )
    )
    advanceUntilIdle()
    vm.toggleItemRemoved(1) // remove B
    vm.saveMeal()
    advanceUntilIdle()
    assertEquals(1, repo.saved.size)
    val saved = repo.saved.first()
    assertEquals(1, saved.items.size)
    assertEquals("A", saved.items.first().name)
    assertEquals(100, saved.totals.calories)
    assertEquals(10, saved.totals.proteinG)
}

@Test
fun `saveMeal does not save when all items excluded`() = runTest(dispatcher) {
    val repo = FakeMealRepo()
    val vm = vmInResultState(repo = repo, items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
    advanceUntilIdle()
    vm.toggleItemRemoved(0)
    vm.saveMeal()
    advanceUntilIdle()
    assertEquals(0, repo.saved.size)
    assertEquals("כל הפריטים הוסרו", vm.errorMessage.value)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: FAIL — compilation error / unresolved reference `excludedIndices`, `toggleItemRemoved`, `addItem`.

- [ ] **Step 3: Add the soft-delete state and methods**

In `AddMealViewModel.kt`, add the state flow next to `_recognizedItems` (after line 42):

```kotlin
    private val _excludedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val excludedIndices: StateFlow<Set<Int>> = _excludedIndices.asStateFlow()
```

Add these methods next to the existing `updateItem` (after line 148). `updateItem` already guards `index in list.indices`, so it stays unchanged:

```kotlin
    fun toggleItemRemoved(index: Int) {
        if (index !in _recognizedItems.value.indices) return
        val current = _excludedIndices.value
        _excludedIndices.value = if (index in current) current - index else current + index
    }

    fun addItem(item: MealItem): Int {
        val list = _recognizedItems.value.toMutableList()
        list.add(item)
        _recognizedItems.value = list
        return list.size - 1
    }
```

- [ ] **Step 4: Filter excluded items in saveMeal, and clear exclusions when items change**

In `saveMeal()`, replace the non-manual branch (currently lines 172-184) with:

```kotlin
            } else {
                val activeItems = _recognizedItems.value
                    .filterIndexed { i, _ -> i !in _excludedIndices.value }
                if (activeItems.isEmpty()) {
                    _errorMessage.value = "כל הפריטים הוסרו"
                    return@launch
                }
                items = activeItems
                totals = MealTotals(
                    calories = activeItems.sumOf { it.calories },
                    proteinG = activeItems.sumOf { it.proteinG },
                    carbsG = activeItems.sumOf { it.carbsG },
                    fatG = activeItems.sumOf { it.fatG }
                )
            }
```

A fresh analysis replaces the item list, so stale exclusions must be cleared. Add `_excludedIndices.value = emptySet()` immediately after each `_recognizedItems.value = result.items` assignment — in `analyzeImageUri` (after line 111) and in `runAnalysis` (after line 130). Also clear it in `switchToManualFallback()` and `resetToInput()` by adding `_excludedIndices.value = emptySet()` alongside the existing resets.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.myhealthtracker.app.ui.meal.AddMealViewModelTest"`
Expected: PASS — all tests including the five new ones and the existing ones.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt
git commit -m "feat(meal): soft-delete state and excluded-aware save in AddMealViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Soft-delete UI in ResultStateContent

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`

**Interfaces:**
- Consumes (from Task 1): `viewModel.excludedIndices: StateFlow<Set<Int>>`, `viewModel.toggleItemRemoved(index)`, `viewModel.addItem(item): Int`, `viewModel.updateItem(index, item)`.
- Produces: updated `ResultStateContent` signature with `excludedIndices: Set<Int>`, `onToggleRemoved: (Int) -> Unit`, `onItemAdd: (MealItem) -> Int`.

Note: Compose UI here is verified by build + `@Preview`, matching the project's existing approach (no Compose UI unit tests exist).

- [ ] **Step 1: Add icon imports**

In `AddMealScreen.kt`, add to the icon import block (near lines 17-24):

```kotlin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
```

- [ ] **Step 2: Update the call site in `AddMealScreen`**

Collect the new flow near the other `collectAsState()` calls (after line 94):

```kotlin
    val excludedIndices by viewModel.excludedIndices.collectAsState()
```

Update the `ResultStateContent(...)` call (currently lines 226-236) to pass the new args and wire the callbacks:

```kotlin
                ResultStateContent(
                    recognizedItems = recognizedItems,
                    excludedIndices = excludedIndices,
                    lowConfidence = lowConfidence,
                    recommendation = recommendation,
                    quality = quality,
                    errorMessage = errorMessage,
                    onItemUpdate = { index, item -> viewModel.updateItem(index, item) },
                    onToggleRemoved = { index -> viewModel.toggleItemRemoved(index) },
                    onItemAdd = { item -> viewModel.addItem(item) },
                    onSaveClick = { viewModel.saveMeal() },
                    onManualClick = { viewModel.switchToManualFallback() },
                    modifier = contentModifier
                )
```

- [ ] **Step 3: Update the `ResultStateContent` signature and totals source**

Replace the signature (lines 439-450) and the local-state/totals block (lines 451-461) with:

```kotlin
@Composable
private fun ResultStateContent(
    recognizedItems: List<MealItem>,
    excludedIndices: Set<Int>,
    lowConfidence: Boolean,
    recommendation: String?,
    quality: MealQuality?,
    errorMessage: String?,
    onItemUpdate: (Int, MealItem) -> Unit,
    onToggleRemoved: (Int) -> Unit,
    onItemAdd: (MealItem) -> Int,
    onSaveClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    // Active items = not marked removed. Totals derive from these only.
    val activeItems = recognizedItems.filterIndexed { i, _ -> i !in excludedIndices }
    val totals = MealTotals(
        calories = activeItems.sumOf { it.calories },
        proteinG = activeItems.sumOf { it.proteinG },
        carbsG = activeItems.sumOf { it.carbsG },
        fatG = activeItems.sumOf { it.fatG }
    )
```

This removes the `var itemsList by remember(recognizedItems)` line entirely. Every later reference to `itemsList` is replaced with `recognizedItems` (see Steps 4-6).

- [ ] **Step 4: Render removed state in the item list**

In the `itemsIndexed(...)` block, change `itemsIndexed(itemsList)` to `itemsIndexed(recognizedItems)`. Immediately inside the lambda (before the `Card`), compute the removed flag:

```kotlin
        itemsIndexed(recognizedItems) { index, item ->
            val isRemoved = index in excludedIndices
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isRemoved) Modifier.alpha(0.5f) else Modifier)
            ) {
```

In the static (non-editing) view, apply strikethrough to the item name `Text` (currently around lines 824-828):

```kotlin
                                    Text(
                                        text = item.name.ifEmpty { "פריט ללא שם" },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = if (isRemoved) TextDecoration.LineThrough else null
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
```

- [ ] **Step 5: Add the delete/restore icon button next to Edit**

In the static view header `Row` (the `IconButton` for Edit at lines 837-846), wrap the edit button and the new toggle button in a small `Row`. When the item is removed, the edit button is hidden (you can't edit a removed item) and only restore shows:

```kotlin
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!isRemoved) {
                                    IconButton(
                                        onClick = { editingIndex = index },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "ערוך",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleRemoved(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRemoved) Icons.Default.Undo else Icons.Default.Delete,
                                        contentDescription = if (isRemoved) "שחזר" else "הסר",
                                        tint = if (isRemoved) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
```

- [ ] **Step 6: Fix the broken "add item" and disable Save when empty**

In the "Add Item Dotted Card" `clickable` (currently lines 912-916), use the new `onItemAdd` return value:

```kotlin
                    .clickable {
                        val newItem = MealItem("", "100 גרם", 0, 0, 0, 0)
                        val newIndex = onItemAdd(newItem)
                        editingIndex = newIndex
                    },
```

In the Save `Button` (lines 946-960), disable it when there are no active items:

```kotlin
                Button(
                    onClick = onSaveClick,
                    enabled = activeItems.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("שמירה", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
```

Add a hint shown only when everything is removed, directly under the Save button (inside the same `Column`, before the `TextButton`):

```kotlin
                if (recognizedItems.isNotEmpty() && activeItems.isEmpty()) {
                    Text(
                        text = "כל הפריטים הוסרו",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

- [ ] **Step 7: Update the `@Preview` for the result state**

Update `AddMealScreenPreviewResult` (lines 1153-1171) to pass the new args:

```kotlin
        ResultStateContent(
            recognizedItems = listOf(
                MealItem("חזה עוף", "150 גרם", 250, 46, 0, 5),
                MealItem("שמן זית", "1 כף", 120, 0, 0, 14)
            ),
            excludedIndices = setOf(1),
            lowConfidence = false,
            recommendation = null,
            quality = null,
            errorMessage = null,
            onItemUpdate = { _, _ -> },
            onToggleRemoved = {},
            onItemAdd = { 0 },
            onSaveClick = {},
            onManualClick = {}
        )
```

- [ ] **Step 8: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. The result screen now shows a trash icon per item; tapping it strikes through the item, dims the card, swaps the icon to restore, and the totals card drops that item's calories/macros. Restoring re-adds it. With all items removed, Save is disabled and the "כל הפריטים הוסרו" hint shows.

- [ ] **Step 9: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no regressions in `AddMealViewModelTest` or other suites).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt
git commit -m "feat(meal): soft-delete UI with strikethrough + restore in result screen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- ViewModel single source of truth + `excludedIndices` + `toggleItemRemoved` + `addItem` + excluded-aware `saveMeal` → Task 1. ✓
- Remove duplicate local `itemsList`; totals from active items → Task 2, Step 3. ✓
- Strikethrough + dim + restore icon → Task 2, Steps 4-5. ✓
- Fix broken "add item" → Task 2, Step 6. ✓
- All items removed → Save disabled + hint, exit via existing X (no auto-close) → Task 2, Step 6. ✓
- Tests (toggle, out-of-range, addItem, save-skips-excluded, save-all-excluded) → Task 1, Step 1. ✓
- Out-of-scope items (saved-meal delete, swipe, animations, reorder) explicitly excluded in spec; no tasks added. ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases" placeholders; every code step shows full code. ✓

**Type consistency:** `excludedIndices: Set<Int>` / `StateFlow<Set<Int>>`, `toggleItemRemoved(index: Int)`, `addItem(item: MealItem): Int` used identically across Task 1 (definition) and Task 2 (consumption + preview). Save hint text `"כל הפריטים הוסרו"` matches between ViewModel error and UI hint. ✓
