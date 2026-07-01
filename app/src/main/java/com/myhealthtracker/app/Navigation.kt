package com.myhealthtracker.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.QuickActionsNotificationManager
import com.myhealthtracker.app.ui.auth.AuthScreen
import com.myhealthtracker.app.ui.auth.AuthViewModel
import com.myhealthtracker.app.ui.body.AddBodyMeasurementScreen
import com.myhealthtracker.app.ui.main.MainScreen
import com.myhealthtracker.app.ui.meal.AddMealScreen
import com.myhealthtracker.app.ui.meal.MealEditScreen
import com.myhealthtracker.app.ui.meal.pickUnseenMealToShow
import com.myhealthtracker.app.ui.profile.ProfileScreen
import com.myhealthtracker.app.ui.profile.ProfileViewModel
import com.myhealthtracker.app.ui.reminders.ReminderSettingsScreen
import com.myhealthtracker.app.ui.workout.AddWorkoutScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
  intent: Intent? = null,
  onIntentHandled: () -> Unit = {}
) {
  // Cold-start straight into the app if a session already exists; otherwise show Auth.
  // The post-auth profile check (below) still decides Profile-setup vs Dashboard.
  val startKey = if (AppContainer.currentUid() != null) Dashboard else Auth
  val backStack = rememberNavBackStack(startKey)
  val scope = rememberCoroutineScope()

  // Deep-link target carried by a notification action. Applied immediately when a
  // session already exists, otherwise it's held until sign-in completes (see
  // routeAfterAuth) so a tap from the lock/signed-out state isn't lost.
  var pendingDestination by remember { mutableStateOf<String?>(null) }
  var pendingMealId by remember { mutableStateOf<String?>(null) }
  // True for this launch when it came from a quick-action add button; suppresses the
  // unseen-meal interception so the user lands on the screen they asked for.
  var suppressUnseenInterception by remember { mutableStateOf(false) }

  fun applyPendingDestination() {
    when (pendingDestination) {
      QuickActionsNotificationManager.DEST_ADD_MEAL -> backStack.add(AddMeal)
      QuickActionsNotificationManager.DEST_ADD_WORKOUT -> backStack.add(AddWorkout)
      QuickActionsNotificationManager.DEST_MEAL_RESULT -> pendingMealId?.let { backStack.add(EditMeal(it, celebrate = true)) }
    }
    pendingDestination = null
    pendingMealId = null
    // Re-arm the unseen-meal interception. The quick-add screen is still protected because
    // the interception only acts when backStack.lastOrNull() == Dashboard, and right now
    // the top is AddMeal/AddWorkout; the unseen meal surfaces only after the user returns.
    suppressUnseenInterception = false
  }

  LaunchedEffect(intent) {
    val destination = intent?.getStringExtra(QuickActionsNotificationManager.EXTRA_NAVIGATE_TO)
    if (destination != null) {
      pendingDestination = destination
      pendingMealId = intent.getStringExtra(QuickActionsNotificationManager.EXTRA_MEAL_ID)
      suppressUnseenInterception =
        destination == QuickActionsNotificationManager.DEST_ADD_MEAL ||
        destination == QuickActionsNotificationManager.DEST_ADD_WORKOUT
      onIntentHandled()
      if (AppContainer.currentUid() != null) applyPendingDestination()
    }
  }

  // Unseen-meal interception: when the user returns to the Dashboard and there is a
  // completed meal they haven't seen yet, pop it automatically. Skipped when the launch
  // originated from a quick-action add button so the user lands where they asked.
  LaunchedEffect(suppressUnseenInterception) {
    if (suppressUnseenInterception) return@LaunchedEffect
    AppContainer.mealRepository.meals.collect { meals ->
      if (backStack.lastOrNull() != Dashboard) return@collect
      val unseen = pickUnseenMealToShow(meals) ?: return@collect
      // Only the newest unseen-complete meal is surfaced; the rest are marked seen so they don't queue a stack of screens (they remain viewable in the journal).
      meals.filter { it.status == MealStatus.COMPLETE && !it.seen }
        .forEach { AppContainer.mealRepository.markMealSeen(it.mealId) }
      backStack.add(EditMeal(unseen.mealId, celebrate = true))
    }
  }

  val authViewModel: AuthViewModel = viewModel()
  val profileViewModel: ProfileViewModel = viewModel()

  // After sign-in: first-time users (no profile yet) go to Profile setup, returning
  // users go straight to the Dashboard. Replaces the back stack so Auth/setup can't be
  // reached with the back button.
  fun routeAfterAuth() {
    val uid = AppContainer.currentUid() ?: return
    scope.launch {
      val profile = AppContainer.profileRepository.getUserProfile(uid).first().getOrNull()
      backStack.clear()
      backStack.add(if (profile == null) Profile else Dashboard)
      // A notification tap that arrived while signed out is honored now that we have
      // a session and a Dashboard to layer the target screen on top of.
      if (profile != null) applyPendingDestination()
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Auth> {
          AuthScreen(
            viewModel = authViewModel,
            onAuthSuccess = { routeAfterAuth() },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<Profile> {
          val context = LocalContext.current
          ProfileScreen(
            viewModel = profileViewModel,
            onSaveSuccess = {
              backStack.clear()
              backStack.add(Dashboard)
            },
            onLogout = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            },
            onAccountDeleted = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            },
            onNavigateToReminderSettings = { backStack.add(ReminderSettingsRoute) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<Dashboard> {
          val context = LocalContext.current
          MainScreen(
            onNavigateToProfile = { backStack.add(Profile) },
            onNavigateToAddMeasurement = { backStack.add(AddBodyMeasurement) },
            onNavigateToAddWorkout = { backStack.add(AddWorkout) },
            onNavigateToAddMeal = { backStack.add(AddMeal) },
            onNavigateToEditMeal = { backStack.add(EditMeal(it)) },
            onLogout = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            }
          )
        }
        entry<EditMeal> { key ->
          val meals by AppContainer.mealRepository.meals.collectAsState()
          val meal = meals.firstOrNull { it.mealId == key.mealId }
          when {
            meal != null -> MealEditScreen(
              meal = meal,
              celebrateOnOpen = key.celebrate, // intent carried on key; avoids Firestore-timing race with markMealSeen
              onDismiss = { backStack.removeLastOrNull() }
            )
            // meals StateFlow starts empty and loads async (e.g. notification cold start);
            // show a loader instead of dismissing before the meal arrives.
            meals.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
            // Loaded but this id isn't present (e.g. deleted) — back out.
            else -> LaunchedEffect(key.mealId) { backStack.removeLastOrNull() }
          }
        }
        entry<AddWorkout> {
          AddWorkoutScreen(
            onDismiss = { backStack.removeLastOrNull() }
          )
        }
        entry<AddMeal> {
          AddMealScreen(
            onDismiss = { backStack.removeLastOrNull() }
          )
        }
        entry<ReminderSettingsRoute> {
          val context = LocalContext.current
          ReminderSettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            onGrantOverlay = {
              context.startActivity(
                Intent(
                  AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}")
                )
              )
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<AddBodyMeasurement> {
          AddBodyMeasurementScreen(
            onDismiss = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
