package com.myhealthtracker.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.myhealthtracker.app.ui.auth.AuthScreen
import com.myhealthtracker.app.ui.auth.AuthViewModel
import com.myhealthtracker.app.ui.profile.ProfileScreen
import com.myhealthtracker.app.ui.profile.ProfileViewModel
import com.myhealthtracker.app.ui.dashboard.DashboardScreen
import com.myhealthtracker.app.ui.dashboard.DashboardViewModel

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Auth)

  val authViewModel: AuthViewModel = viewModel()
  val profileViewModel: ProfileViewModel = viewModel()
  val dashboardViewModel: DashboardViewModel = viewModel()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Auth> {
          AuthScreen(
            viewModel = authViewModel,
            onAuthSuccess = {
              backStack.add(Dashboard)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<Profile> {
          ProfileScreen(
            viewModel = profileViewModel,
            onSaveSuccess = {
              backStack.add(Dashboard)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<Dashboard> {
          DashboardScreen(
            viewModel = dashboardViewModel,
            onNavigateToProfile = {
              backStack.add(Profile)
            },
            onLogout = {
              authViewModel.signOut()
              backStack.add(Auth)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
      },
  )
}
