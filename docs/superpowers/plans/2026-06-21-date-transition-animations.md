# Date Transition Animations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fade transition animation when switching days on both food and workout screens, and color the selected date card slate-gray when the selected date is not the current day.

**Architecture:** Define custom Slate colors in Color.kt. Wrap the main content LazyColumn on both FoodScreen.kt and ActivityScreen.kt with a Compose Crossfade layout driven by selectedDate. Conditionally render the containerColor of selected date cards based on whether it is LocalDate.now().

**Tech Stack:** Jetpack Compose, Kotlin, Android SDK

---

### Task 1: Add Custom Slate Colors

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/theme/Color.kt`

- [ ] **Step 1: Add Slate colors to Color.kt**
  Add the following lines to `app/src/main/java/com/myhealthtracker/app/theme/Color.kt`:
  ```kotlin
  val SlateSelectedLight = Color(0xFF546E7A)
  val SlateSelectedDark = Color(0xFF78909C)
  ```

- [ ] **Step 2: Compile project to verify no errors**
  Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit changes**
  ```bash
  git add app/src/main/java/com/myhealthtracker/app/theme/Color.kt
  git commit -m "feat: add slate gray colors for non-today selected date cards"
  ```

---

### Task 2: Implement Slate Color and Transition in FoodScreen

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt`

- [ ] **Step 1: Modify imports and add color & transition logic to FoodScreen.kt**
  Add `import androidx.compose.animation.Crossfade` and `import androidx.compose.foundation.isSystemInDarkTheme` to the top of `app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt`.
  
  In the `FoodContent` composable:
  
  For the calendar strip card `containerColor`:
  ```kotlin
  containerColor = if (isSelected) {
      if (date == LocalDate.now()) {
          MaterialTheme.colorScheme.primary
      } else {
          if (isSystemInDarkTheme()) SlateSelectedDark else SlateSelectedLight
      }
  } else {
      MaterialTheme.colorScheme.surface
  }
  ```

  Wrap the `LazyColumn` containing the food data (under "// 1. AI Suggestion Card" onwards) with `Crossfade`:
  ```kotlin
  Crossfade(
      targetState = state.selectedDate,
      animationSpec = tween(durationMillis = 300),
      label = "FoodDateTransition",
      modifier = Modifier.weight(1f)
  ) { targetDate ->
      LazyColumn(
          modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = PaddingValues(bottom = 88.dp)
      ) {
          // Inner items...
      }
  }
  ```
  *(Make sure to use the state inside the LazyColumn elements)*

- [ ] **Step 2: Run build and unit tests**
  Run: `./gradlew test` and `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL and tests pass

- [ ] **Step 3: Commit changes**
  ```bash
  git add app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt
  git commit -m "feat: add date crossfade animation and slate selected state to FoodScreen"
  ```

---

### Task 3: Implement Slate Color and Transition in ActivityScreen

**Files:**
- Modify: `app/src/main/java/com/myhealthtracker/app/ui/activity/ActivityScreen.kt`

- [ ] **Step 1: Modify imports and add color & transition logic to ActivityScreen.kt**
  Add `import androidx.compose.animation.Crossfade` and `import androidx.compose.foundation.isSystemInDarkTheme` and `import androidx.compose.animation.core.tween` to `app/src/main/java/com/myhealthtracker/app/ui/activity/ActivityScreen.kt`.
  
  In the `ActivityContent` composable:
  
  For the calendar strip card `containerColor`:
  ```kotlin
  containerColor = if (isSelected) {
      if (date == LocalDate.now()) {
          MaterialTheme.colorScheme.primary
      } else {
          if (isSystemInDarkTheme()) SlateSelectedDark else SlateSelectedLight
      }
  } else {
      MaterialTheme.colorScheme.surface
  }
  ```

  Wrap the `LazyColumn` containing the activity/workout data (under "// 1. Steps Card" onwards) with `Crossfade`:
  ```kotlin
  Crossfade(
      targetState = state.selectedDate,
      animationSpec = tween(durationMillis = 300),
      label = "ActivityDateTransition",
      modifier = Modifier.weight(1f)
  ) { targetDate ->
      LazyColumn(
          modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
      ) {
          // Inner items...
      }
  }
  ```

- [ ] **Step 2: Run build and unit tests**
  Run: `./gradlew test` and `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL and tests pass

- [ ] **Step 3: Commit changes**
  ```bash
  git add app/src/main/java/com/myhealthtracker/app/ui/activity/ActivityScreen.kt
  git commit -m "feat: add date crossfade animation and slate selected state to ActivityScreen"
  ```
