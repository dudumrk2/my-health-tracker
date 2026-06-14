package com.myhealthtracker.app.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.*
import com.myhealthtracker.app.ui.dashboard.DashboardScreen
import com.myhealthtracker.app.ui.dashboard.DashboardViewModel
import com.myhealthtracker.app.ui.activity.ActivityScreen
import com.myhealthtracker.app.ui.activity.ActivityViewModel
import com.myhealthtracker.app.ui.food.FoodScreen
import com.myhealthtracker.app.ui.food.FoodViewModel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

enum class MainTab(val label: String, val icon: ImageVector) {
    Dashboard("דשבורד", Icons.Default.Dashboard),
    Food("אוכל", Icons.Default.Restaurant),
    Activity("פעילות", Icons.Default.FitnessCenter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToAddMeasurement: () -> Unit,
    onNavigateToAddWorkout: () -> Unit,
    onNavigateToAddMeal: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Dashboard) }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val activityViewModel: ActivityViewModel = viewModel()
    val foodViewModel: FoodViewModel = viewModel()

    Box(modifier = modifier.fillMaxSize()) {
        // Screen Content with Fade/Slide Transition
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { height -> height } + fadeIn()).togetherWith(
                            slideOutHorizontally { height -> -height } + fadeOut())
                    } else {
                        (slideInHorizontally { height -> -height } + fadeIn()).togetherWith(
                            slideOutHorizontally { height -> height } + fadeOut())
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    MainTab.Dashboard -> {
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToAddMeasurement = onNavigateToAddMeasurement,
                            onLogout = onLogout,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    MainTab.Activity -> {
                        ActivityScreen(
                            viewModel = activityViewModel,
                            onNavigateToAddWorkout = onNavigateToAddWorkout,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    MainTab.Food -> {
                        FoodScreen(
                            viewModel = foodViewModel,
                            onNavigateToAddMeal = onNavigateToAddMeal,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Custom Floating Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            CustomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    }
}

@Composable
fun CustomNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(36.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                spotColor = MaterialTheme.colorScheme.primary
            ),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                
                // Animated indicator and scale
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "IconScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Custom indication handled by Box background
                        ) { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    // Background Pill for selected item
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(iconScale),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        androidx.compose.animation.AnimatedVisibility(visible = isSelected) {
                            Text(
                                text = tab.label,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

