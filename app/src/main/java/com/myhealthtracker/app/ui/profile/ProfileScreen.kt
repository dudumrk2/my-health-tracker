package com.myhealthtracker.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.myhealthtracker.app.data.goals.ACTIVITY_LEVEL_OPTIONS
import com.myhealthtracker.app.data.goals.FOCUS_AREA_OPTIONS
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.goals.HEALTH_DISCLAIMER_HE
import com.myhealthtracker.app.data.goals.HealthGoals
import com.myhealthtracker.app.data.goals.PRIMARY_GOAL_OPTIONS
import com.myhealthtracker.app.data.profile.GoalOverrides
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import androidx.compose.ui.res.stringResource
import com.myhealthtracker.app.R

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSaveSuccess: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    onNavigateToReminderSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val calculatedAge by viewModel.calculatedAge.collectAsState()
    val accountState by viewModel.accountState.collectAsState()

    var firstName by rememberSaveable { mutableStateOf("") }
    var birthYearStr by rememberSaveable { mutableStateOf("") }
    var weightStr by rememberSaveable { mutableStateOf("") }
    var heightStr by rememberSaveable { mutableStateOf("") }
    var selectedGender by rememberSaveable { mutableStateOf("") }
    var themePreference by rememberSaveable { mutableStateOf("system") }
    var language by rememberSaveable { mutableStateOf("he") }
    var primaryGoal by rememberSaveable { mutableStateOf("maintain") }
    var activityLevel by rememberSaveable { mutableStateOf("moderate") }
    var focusAreas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var quickActionsEnabled by rememberSaveable { mutableStateOf(true) }
    var celebrationSoundEnabled by rememberSaveable { mutableStateOf(true) }
    // Manual goal overrides (blank = use computed value).
    var caloriesOverride by rememberSaveable { mutableStateOf("") }
    var stepsOverride by rememberSaveable { mutableStateOf("") }
    var proteinOverride by rememberSaveable { mutableStateOf("") }
    var waterOverride by rememberSaveable { mutableStateOf("") }
    var sleepOverride by rememberSaveable { mutableStateOf("") }

    // Tracks whether the user has manually interacted with certain fields, to prevent 
    // the background sync/recreation from overwriting their unsaved changes.
    var hasToggledLanguage by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Loaded) {
            val profile = (uiState as ProfileUiState.Loaded).profile
            // Only update local state if it's currently empty/default or hasn't been touched,
            // to avoid overwriting user's unsaved edits during a sync or recreation.
            if (firstName.isEmpty()) firstName = profile.firstName
            if (birthYearStr.isEmpty()) birthYearStr = if (profile.birthYear > 0) profile.birthYear.toString() else ""
            if (weightStr.isEmpty()) weightStr = if (profile.weightKg > 0.0) profile.weightKg.toString() else ""
            if (heightStr.isEmpty()) heightStr = if (profile.heightCm > 0.0) profile.heightCm.toString() else ""
            if (selectedGender.isEmpty()) selectedGender = profile.gender
            
            if (themePreference == "system") themePreference = profile.themePreference
            if (!hasToggledLanguage) language = profile.language
            if (primaryGoal == "maintain") primaryGoal = profile.primaryGoal
            if (activityLevel == "moderate") activityLevel = profile.activityLevel
            if (focusAreas.isEmpty()) focusAreas = profile.focusAreas.toSet()

            quickActionsEnabled = profile.quickActionsEnabled
            celebrationSoundEnabled = profile.celebrationSoundEnabled
            
            profile.goalOverrides?.let { o ->
                if (caloriesOverride.isEmpty()) caloriesOverride = o.caloriesKcal?.toString() ?: ""
                if (stepsOverride.isEmpty()) stepsOverride = o.steps?.toString() ?: ""
                if (proteinOverride.isEmpty()) proteinOverride = o.proteinG?.toString() ?: ""
                if (waterOverride.isEmpty()) waterOverride = o.waterMl?.toString() ?: ""
                if (sleepOverride.isEmpty()) sleepOverride = o.sleepHours?.toString() ?: ""
            }
            viewModel.updateAge(profile.birthYear)
        } else if (uiState is ProfileUiState.Saved) {
            viewModel.resetState()
            onSaveSuccess()
        }
    }

    LaunchedEffect(accountState) {
        if (accountState is AccountState.Deleted) {
            onAccountDeleted()
        }
    }

    fun buildOverrides(): GoalOverrides? {
        val o = GoalOverrides(
            caloriesKcal = caloriesOverride.toIntOrNull(),
            steps = stepsOverride.toIntOrNull(),
            proteinG = proteinOverride.toIntOrNull(),
            waterMl = waterOverride.toIntOrNull(),
            sleepHours = sleepOverride.toIntOrNull()
        )
        val empty = o.caloriesKcal == null && o.steps == null && o.proteinG == null &&
            o.waterMl == null && o.sleepHours == null
        return if (empty) null else o
    }

    val goals = viewModel.previewGoals(
        birthYearStr, weightStr, heightStr, selectedGender, primaryGoal, activityLevel, buildOverrides()
    )

    ProfileScreenContent(
        uiState = uiState,
        firstName = firstName,
        birthYearStr = birthYearStr,
        weightStr = weightStr,
        heightStr = heightStr,
        selectedGender = selectedGender,
        themePreference = themePreference,
        language = language,
        primaryGoal = primaryGoal,
        activityLevel = activityLevel,
        focusAreas = focusAreas,
        caloriesOverride = caloriesOverride,
        stepsOverride = stepsOverride,
        proteinOverride = proteinOverride,
        waterOverride = waterOverride,
        sleepOverride = sleepOverride,
        goals = goals,
        calculatedAge = calculatedAge,
        onFirstNameChange = { firstName = it },
        onBirthYearChange = {
            birthYearStr = it
            it.toIntOrNull()?.let { year -> viewModel.updateAge(year) }
        },
        onWeightChange = { weightStr = it },
        onHeightChange = { heightStr = it },
        onGenderSelect = { selectedGender = it },
        onThemeSelect = { themePreference = it },
        onLanguageSelect = { 
            language = it
            hasToggledLanguage = true
        },
        onPrimaryGoalSelect = { primaryGoal = it },
        onActivityLevelSelect = { activityLevel = it },
        onFocusAreaToggle = { value ->
            focusAreas = if (value in focusAreas) focusAreas - value else focusAreas + value
        },
        onCaloriesOverrideChange = { caloriesOverride = it },
        onStepsOverrideChange = { stepsOverride = it },
        onProteinOverrideChange = { proteinOverride = it },
        onWaterOverrideChange = { waterOverride = it },
        onSleepOverrideChange = { sleepOverride = it },
        onSaveClick = {
            viewModel.saveProfile(
                firstName, birthYearStr, weightStr, heightStr, selectedGender, themePreference, language,
                primaryGoal, activityLevel, focusAreas.toList(), buildOverrides(),
                quickActionsEnabled, celebrationSoundEnabled
            )
        },
        onBackClick = {
            viewModel.resetState()
            onSaveSuccess()
        },
        quickActionsEnabled = quickActionsEnabled,
        onQuickActionsEnabledChange = { quickActionsEnabled = it },
        celebrationSoundEnabled = celebrationSoundEnabled,
        onCelebrationSoundEnabledChange = { celebrationSoundEnabled = it },
        accountState = accountState,
        onLogoutClick = onLogout,
        onDeleteAccountConfirm = { viewModel.deleteAccount() },
        onNavigateToReminderSettings = onNavigateToReminderSettings,
        modifier = modifier
    )
}

/** Single-select chip row reused for gender/theme/goal/activity selections. */
@Composable
private fun SelectRow(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = selectedValue == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ProfileScreenContent(
    uiState: ProfileUiState,
    firstName: String,
    birthYearStr: String,
    weightStr: String,
    heightStr: String,
    selectedGender: String,
    themePreference: String,
    language: String,
    primaryGoal: String,
    activityLevel: String,
    focusAreas: Set<String>,
    caloriesOverride: String,
    stepsOverride: String,
    proteinOverride: String,
    waterOverride: String,
    sleepOverride: String,
    goals: HealthGoals,
    calculatedAge: Int,
    onFirstNameChange: (String) -> Unit,
    onBirthYearChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onGenderSelect: (String) -> Unit,
    onThemeSelect: (String) -> Unit,
    onLanguageSelect: (String) -> Unit,
    onPrimaryGoalSelect: (String) -> Unit,
    onActivityLevelSelect: (String) -> Unit,
    onFocusAreaToggle: (String) -> Unit,
    onCaloriesOverrideChange: (String) -> Unit,
    onStepsOverrideChange: (String) -> Unit,
    onProteinOverrideChange: (String) -> Unit,
    onWaterOverrideChange: (String) -> Unit,
    onSleepOverrideChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    quickActionsEnabled: Boolean,
    onQuickActionsEnabledChange: (Boolean) -> Unit,
    celebrationSoundEnabled: Boolean,
    onCelebrationSoundEnabledChange: (Boolean) -> Unit,
    accountState: AccountState,
    onLogoutClick: () -> Unit,
    onDeleteAccountConfirm: () -> Unit,
    onNavigateToReminderSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<EditableGoal?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    // Account deletion runs in the background; while it does, the form's cancel/save actions are
    // locked so the user can't navigate away (losing the completion callback) or write the profile
    // back concurrently with the delete.
    val isDeleting = accountState is AccountState.Deleting

    LaunchedEffect(uiState) {
        if (uiState !is ProfileUiState.Loading) isSaving = false
    }
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.background
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onBackClick,
                    enabled = !isDeleting,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.primary)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_welcome_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profile_welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // ── Basic details ──────────────────────────────────────────────
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
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = onFirstNameChange,
                        label = { Text(stringResource(R.string.profile_first_name)) },
                        placeholder = { Text(stringResource(R.string.profile_first_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = birthYearStr,
                        onValueChange = onBirthYearChange,
                        label = { Text(stringResource(R.string.profile_birth_year)) },
                        placeholder = { Text(stringResource(R.string.profile_birth_year_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (calculatedAge > 0) {
                        Text(
                            text = stringResource(R.string.profile_calculated_age, calculatedAge),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Column {
                        FieldLabel(stringResource(R.string.profile_gender))
                        SelectRow(
                            options = listOf(
                                "זכר" to stringResource(R.string.profile_gender_male),
                                "נקבה" to stringResource(R.string.profile_gender_female)
                            ),
                            selectedValue = selectedGender,
                            onSelect = onGenderSelect
                        )
                    }

                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = onWeightChange,
                        label = { Text(stringResource(R.string.profile_weight)) },
                        placeholder = { Text(stringResource(R.string.profile_weight_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = onHeightChange,
                        label = { Text(stringResource(R.string.profile_height)) },
                        placeholder = { Text(stringResource(R.string.profile_height_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // ── Goal & activity ────────────────────────────────────────────
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
                        FieldLabel(stringResource(R.string.profile_primary_goal_field))
                        SelectRow(
                            options = listOf(
                                "lose" to stringResource(R.string.profile_goal_lose),
                                "maintain" to stringResource(R.string.profile_goal_maintain),
                                "gain" to stringResource(R.string.profile_goal_gain)
                            ),
                            selectedValue = primaryGoal,
                            onSelect = onPrimaryGoalSelect
                        )
                    }
                    Column {
                        FieldLabel(stringResource(R.string.profile_activity_level_field))
                        SelectRow(
                            options = listOf(
                                "sedentary" to stringResource(R.string.profile_activity_sedentary),
                                "light" to stringResource(R.string.profile_activity_light),
                                "moderate" to stringResource(R.string.profile_activity_moderate),
                                "very" to stringResource(R.string.profile_activity_very),
                                "extra" to stringResource(R.string.profile_activity_extra)
                            ),
                            selectedValue = activityLevel,
                            onSelect = onActivityLevelSelect
                        )
                    }
                }
            }

            // ── Self-declared focus areas (optional) ───────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FieldLabel(stringResource(R.string.profile_focus_areas_title))
                    val focusOptions = listOf(
                        "menopause" to stringResource(R.string.profile_focus_menopause),
                        "muscle_gain" to stringResource(R.string.profile_focus_muscle_gain),
                        "heart_health" to stringResource(R.string.profile_focus_heart_health)
                    )
                    focusOptions.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFocusAreaToggle(value) }
                        ) {
                            Checkbox(checked = value in focusAreas, onCheckedChange = { onFocusAreaToggle(value) })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(
                        text = stringResource(R.string.health_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── Computed goals + manual overrides ──────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FieldLabel(stringResource(R.string.profile_your_goals))
                    if (goals.isGeneric) {
                        Text(
                            text = stringResource(R.string.profile_generic_goals_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (goals.extremeAdjustmentWarning) {
                        Text(
                            text = stringResource(R.string.profile_extreme_adjustment_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    GoalLine(
                        label = stringResource(R.string.dashboard_calories),
                        value = "${goals.caloriesKcal} ${stringResource(R.string.food_kcal_unit)}",
                        caption = if (goals.tdee > 0) stringResource(R.string.profile_tdee_caption, goals.tdee) else null,
                        onEdit = { editingGoal = EditableGoal.CALORIES }
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_protein),
                        value = "${goals.proteinG} ${stringResource(R.string.food_macro_unit_g)}",
                        onEdit = { editingGoal = EditableGoal.PROTEIN }
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_fat),
                        value = "${goals.fatG} ${stringResource(R.string.food_macro_unit_g)}"
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_carbs),
                        value = "${goals.carbsG} ${stringResource(R.string.food_macro_unit_g)}"
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_steps),
                        value = "${goals.steps}",
                        onEdit = { editingGoal = EditableGoal.STEPS }
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_sleep),
                        value = formatSleepGoal(goals.sleepHoursMin, goals.sleepHoursMax, stringResource(R.string.workout_duration_minutes)),
                        onEdit = { editingGoal = EditableGoal.SLEEP }
                    )
                    GoalLine(
                        label = stringResource(R.string.dashboard_water),
                        value = "${goals.waterMl} ml",
                        onEdit = { editingGoal = EditableGoal.WATER }
                    )

                    Text(
                        text = stringResource(R.string.health_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (uiState is ProfileUiState.Error) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

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
                        text = stringResource(R.string.profile_finish_btn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

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
                        FieldLabel(stringResource(R.string.profile_theme))
                        SelectRow(
                            options = listOf(
                                "system" to stringResource(R.string.profile_theme_system),
                                "light" to stringResource(R.string.profile_theme_light),
                                "dark" to stringResource(R.string.profile_theme_dark)
                            ),
                            selectedValue = themePreference,
                            onSelect = onThemeSelect
                        )
                    }

                    Column {
                        FieldLabel(stringResource(R.string.profile_language))
                        SelectRow(
                            options = listOf(
                                "he" to stringResource(R.string.profile_language_he),
                                "en" to stringResource(R.string.profile_language_en)
                            ),
                            selectedValue = language,
                            onSelect = onLanguageSelect
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
                                text = stringResource(R.string.profile_quick_actions_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.profile_quick_actions_subtitle),
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToReminderSettings() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_reminders_item_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.profile_reminders_item_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_celebrations_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.profile_celebrations_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = celebrationSoundEnabled,
                            onCheckedChange = { onCelebrationSoundEnabledChange(it) }
                        )
                    }
                }
            }

            // ── Account: logout + delete ───────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FieldLabel(stringResource(R.string.profile_account_section))

                    OutlinedButton(
                        onClick = onLogoutClick,
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(stringResource(R.string.profile_logout))
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(stringResource(R.string.profile_delete_account_and_data))
                    }

                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    if (accountState is AccountState.Error) {
                        Text(
                            text = accountState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

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

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                    title = { Text(stringResource(R.string.profile_delete_account_dialog_title)) },
                    text = {
                        Text(stringResource(R.string.profile_delete_account_dialog_text))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteAccountConfirm()
                            },
                            enabled = !isDeleting
                        ) {
                            Text(stringResource(R.string.profile_delete_account_permanently_btn), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isSaving || uiState is ProfileUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { }, // Block touches behind overlay
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
            }
        }
    }
}

/** Goals that map to a GoalOverrides field and can be edited via the pencil dialog. */
private enum class EditableGoal(@param:androidx.annotation.StringRes val titleRes: Int) {
    CALORIES(R.string.profile_edit_goal_calories),
    PROTEIN(R.string.profile_edit_goal_protein),
    STEPS(R.string.profile_edit_goal_steps),
    SLEEP(R.string.profile_edit_goal_sleep),
    WATER(R.string.profile_edit_goal_water)
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
        title = { Text(stringResource(goal.titleRes)) },
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
                    Text(stringResource(R.string.profile_reset_to_computed))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft); onDismiss() }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

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
                            contentDescription = stringResource(R.string.common_edit),
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


@Preview(showBackground = true, name = "Light Theme")
@Composable
fun ProfileScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        ProfileScreenContent(
            uiState = ProfileUiState.Idle,
            firstName = "ישראל",
            birthYearStr = "1995",
            weightStr = "75.0",
            heightStr = "178.0",
            selectedGender = "זכר",
            themePreference = "light",
            language = "he",
            primaryGoal = "maintain",
            activityLevel = "moderate",
            focusAreas = emptySet(),
            caloriesOverride = "",
            stepsOverride = "",
            proteinOverride = "",
            waterOverride = "",
            sleepOverride = "",
            goals = GoalCalculator.compute(UserProfile(birthYear = 1995, weightKg = 75.0, heightCm = 178.0, gender = "male")),
            calculatedAge = 31,
            onFirstNameChange = {},
            onBirthYearChange = {}, onWeightChange = {}, onHeightChange = {}, onGenderSelect = {},
            onThemeSelect = {}, onLanguageSelect = {}, onPrimaryGoalSelect = {}, onActivityLevelSelect = {}, onFocusAreaToggle = {},
            onCaloriesOverrideChange = {}, onStepsOverrideChange = {}, onProteinOverrideChange = {},
            onWaterOverrideChange = {}, onSleepOverrideChange = {}, onSaveClick = {}, onBackClick = {},
            quickActionsEnabled = true, onQuickActionsEnabledChange = {},
            celebrationSoundEnabled = true, onCelebrationSoundEnabledChange = {},
            accountState = AccountState.Idle,
            onLogoutClick = {}, onDeleteAccountConfirm = {}
        )
    }
}
