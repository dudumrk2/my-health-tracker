package com.myhealthtracker.app.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.myhealthtracker.app.*
import com.myhealthtracker.app.ui.dashboard.DashboardScreen
import com.myhealthtracker.app.ui.dashboard.DashboardViewModel
import com.myhealthtracker.app.ui.activity.ActivityScreen
import com.myhealthtracker.app.ui.activity.ActivityViewModel
import com.myhealthtracker.app.ui.food.FoodScreen
import com.myhealthtracker.app.ui.food.FoodViewModel

enum class MainTab {
    Dashboard,
    Activity,
    Food
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    backStack: NavBackStack<NavKey>,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Dashboard) }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val activityViewModel: ActivityViewModel = viewModel()
    val foodViewModel: FoodViewModel = viewModel()

    Scaffold(
        modifier = modifier.safeDrawingPadding(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Dashboard,
                    onClick = { selectedTab = MainTab.Dashboard },
                    icon = { Text("📊") }, // Dashboard icon representation
                    label = { Text("דשבורד") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Activity,
                    onClick = { selectedTab = MainTab.Activity },
                    icon = { Text("🏃") }, // Activity icon representation
                    label = { Text("פעילות") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Food,
                    onClick = { selectedTab = MainTab.Food },
                    icon = { Text("🥗") }, // Food/Meals icon representation
                    label = { Text("אוכל") }
                )
            }
        }
    ) { paddingValues ->
        val innerModifier = Modifier.padding(paddingValues)

        when (selectedTab) {
            MainTab.Dashboard -> {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToProfile = { backStack.add(Profile) },
                    onNavigateToAddMeasurement = { backStack.add(AddBodyMeasurement) },
                    onLogout = onLogout,
                    modifier = innerModifier
                )
            }
            MainTab.Activity -> {
                ActivityScreen(
                    viewModel = activityViewModel,
                    onNavigateToAddWorkout = { backStack.add(AddWorkout) },
                    modifier = innerModifier
                )
            }
            MainTab.Food -> {
                FoodScreen(
                    viewModel = foodViewModel,
                    onNavigateToAddMeal = { backStack.add(AddMeal) },
                    modifier = innerModifier
                )
            }
        }
    }
}
