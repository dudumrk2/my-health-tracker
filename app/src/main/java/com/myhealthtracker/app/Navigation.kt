package com.myhealthtracker.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.ui.auth.AuthScreen
import com.myhealthtracker.app.ui.auth.AuthViewModel
import com.myhealthtracker.app.ui.profile.ProfileScreen
import com.myhealthtracker.app.ui.profile.ProfileViewModel
import com.myhealthtracker.app.ui.main.MainScreen
import com.myhealthtracker.app.ui.workout.AddWorkoutScreen
import com.myhealthtracker.app.ui.meal.AddMealScreen
import com.myhealthtracker.app.ui.body.AddBodyMeasurementScreen
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.myhealthtracker.app.notification.QuickActionsNotificationManager
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

  fun applyPendingDestination() {
    when (pendingDestination) {
      QuickActionsNotificationManager.DEST_ADD_MEAL -> backStack.add(AddMeal)
      QuickActionsNotificationManager.DEST_ADD_WORKOUT -> backStack.add(AddWorkout)
    }
    pendingDestination = null
  }

  LaunchedEffect(intent) {
    val destination = intent?.getStringExtra(QuickActionsNotificationManager.EXTRA_NAVIGATE_TO)
    if (destination != null) {
      pendingDestination = destination
      onIntentHandled()
      if (AppContainer.currentUid() != null) {
        applyPendingDestination()
      }
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
          ProfileScreen(
            viewModel = profileViewModel,
            onSaveSuccess = {
              backStack.clear()
              backStack.add(Dashboard)
            },
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
            onLogout = {
              authViewModel.signOut(context)
              backStack.clear()
              backStack.add(Auth)
            }
          )
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
        entry<AddBodyMeasurement> {
          AddBodyMeasurementScreen(
            onDismiss = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
