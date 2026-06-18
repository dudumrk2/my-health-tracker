package com.myhealthtracker.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.myhealthtracker.app.data.goals.ACTIVITY_LEVEL_OPTIONS
import com.myhealthtracker.app.data.goals.FOCUS_AREA_OPTIONS
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.goals.HEALTH_DISCLAIMER_HE
import com.myhealthtracker.app.data.goals.HealthGoals
import com.myhealthtracker.app.data.goals.PRIMARY_GOAL_OPTIONS
import com.myhealthtracker.app.data.profile.GoalOverrides
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.theme.MyHealthTrackerTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSaveSuccess: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val calculatedAge by viewModel.calculatedAge.collectAsState()
    val accountState by viewModel.accountState.collectAsState()

    var birthYearStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var heightStr by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }
    var themePreference by remember { mutableStateOf("system") }
    var primaryGoal by remember { mutableStateOf("maintain") }
    var activityLevel by remember { mutableStateOf("moderate") }
    var focusAreas by remember { mutableStateOf(setOf<String>()) }
    var quickActionsEnabled by remember { mutableStateOf(true) }
    // Manual goal overrides (blank = use computed value).
    var caloriesOverride by remember { mutableStateOf("") }
    var stepsOverride by remember { mutableStateOf("") }
    var proteinOverride by remember { mutableStateOf("") }
    var waterOverride by remember { mutableStateOf("") }
    var sleepOverride by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Loaded) {
            val profile = (uiState as ProfileUiState.Loaded).profile
            birthYearStr = if (profile.birthYear > 0) profile.birthYear.toString() else ""
            weightStr = if (profile.weightKg > 0.0) profile.weightKg.toString() else ""
            heightStr = if (profile.heightCm > 0.0) profile.heightCm.toString() else ""
            selectedGender = profile.gender
            themePreference = profile.themePreference
            primaryGoal = profile.primaryGoal
            activityLevel = profile.activityLevel
            focusAreas = profile.focusAreas.toSet()
            quickActionsEnabled = profile.quickActionsEnabled
            profile.goalOverrides?.let { o ->
                caloriesOverride = o.caloriesKcal?.toString() ?: ""
                stepsOverride = o.steps?.toString() ?: ""
                proteinOverride = o.proteinG?.toString() ?: ""
                waterOverride = o.waterMl?.toString() ?: ""
                sleepOverride = o.sleepHours?.toString() ?: ""
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
        birthYearStr = birthYearStr,
        weightStr = weightStr,
        heightStr = heightStr,
        selectedGender = selectedGender,
        themePreference = themePreference,
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
        onBirthYearChange = {
            birthYearStr = it
            it.toIntOrNull()?.let { year -> viewModel.updateAge(year) }
        },
        onWeightChange = { weightStr = it },
        onHeightChange = { heightStr = it },
        onGenderSelect = { selectedGender = it },
        onThemeSelect = { themePreference = it },
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
                birthYearStr, weightStr, heightStr, selectedGender, themePreference,
                primaryGoal, activityLevel, focusAreas.toList(), buildOverrides(),
                quickActionsEnabled
            )
        },
        onBackClick = {
            viewModel.resetState()
            onSaveSuccess()
        },
        quickActionsEnabled = quickActionsEnabled,
        onQuickActionsEnabledChange = { quickActionsEnabled = it },
        accountState = accountState,
        onLogoutClick = onLogout,
        onDeleteAccountConfirm = { viewModel.deleteAccount() },
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
    birthYearStr: String,
    weightStr: String,
    heightStr: String,
    selectedGender: String,
    themePreference: String,
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
    onBirthYearChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onGenderSelect: (String) -> Unit,
    onThemeSelect: (String) -> Unit,
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
    accountState: AccountState,
    onLogoutClick: () -> Unit,
    onDeleteAccountConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
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
                TextButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("ביטול", color = MaterialTheme.colorScheme.primary)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "בוא נכיר אותך",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "הפרטים יעזרו לנו להתאים את המדדים ותובנות ה-AI במדויק בשבילך",
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
                        value = birthYearStr,
                        onValueChange = onBirthYearChange,
                        label = { Text("שנת לידה") },
                        placeholder = { Text("לדוגמה: 1990") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (calculatedAge > 0) {
                        Text(
                            text = "גיל מחושב: $calculatedAge שנים",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Column {
                        FieldLabel("מין")
                        SelectRow(
                            options = listOf("זכר" to "זכר", "נקבה" to "נקבה"),
                            selectedValue = selectedGender,
                            onSelect = onGenderSelect
                        )
                    }

                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = onWeightChange,
                        label = { Text("משקל (ק״ג)") },
                        placeholder = { Text("לדוגמה: 75.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = onHeightChange,
                        label = { Text("גובה (ס״מ)") },
                        placeholder = { Text("לדוגמה: 178") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

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
                        FieldLabel("מטרת שימוש")
                        SelectRow(PRIMARY_GOAL_OPTIONS, primaryGoal, onPrimaryGoalSelect)
                    }
                    Column {
                        FieldLabel("רמת פעילות")
                        SelectRow(ACTIVITY_LEVEL_OPTIONS, activityLevel, onActivityLevelSelect)
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
                    FieldLabel("תחומים שחשובים לי (אופציונלי)")
                    FOCUS_AREA_OPTIONS.forEach { (value, label) ->
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
                        text = HEALTH_DISCLAIMER_HE,
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
                    GoalLine("קלוריות", "${goals.caloriesKcal} קק\"ל" + if (goals.tdee > 0) "  (TDEE ${goals.tdee})" else "")
                    GoalLine("חלבון", "${goals.proteinG} ג")
                    GoalLine("שומן", "${goals.fatG} ג")
                    GoalLine("פחמימות", "${goals.carbsG} ג")
                    GoalLine("צעדים", "${goals.steps}")
                    GoalLine("שינה", "${goals.sleepHoursMin}-${goals.sleepHoursMax} שעות")
                    GoalLine("מים", "${goals.waterMl} מ\"ל")

                    Spacer(modifier = Modifier.height(4.dp))
                    FieldLabel("דריסה ידנית (אופציונלי)")
                    OverrideField(caloriesOverride, onCaloriesOverrideChange, "קלוריות")
                    OverrideField(stepsOverride, onStepsOverrideChange, "צעדים")
                    OverrideField(proteinOverride, onProteinOverrideChange, "חלבון (ג)")
                    OverrideField(waterOverride, onWaterOverrideChange, "מים (מ\"ל)")
                    OverrideField(sleepOverride, onSleepOverrideChange, "שינה (שעות)")

                    Text(
                        text = HEALTH_DISCLAIMER_HE,
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

            if (uiState is ProfileUiState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = onSaveClick,
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

            // ── Account: logout + delete ───────────────────────────────────
            var showDeleteDialog by remember { mutableStateOf(false) }
            val isDeleting = accountState is AccountState.Deleting

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
                    FieldLabel("חשבון")

                    OutlinedButton(
                        onClick = onLogoutClick,
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("התנתקות")
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
                        Text("מחיקת חשבון ונתונים")
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

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                    title = { Text("מחיקת חשבון ונתונים") },
                    text = {
                        Text(
                            "פעולה זו תמחק לצמיתות את כל הנתונים שלך — ארוחות, נתוני בריאות, " +
                                "תובנות והפרופיל — וגם את החשבון. לא ניתן לבטל פעולה זו."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteAccountConfirm()
                            },
                            enabled = !isDeleting
                        ) {
                            Text("מחק לצמיתות", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                            Text("ביטול")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GoalLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OverrideField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    )
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun ProfileScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        ProfileScreenContent(
            uiState = ProfileUiState.Idle,
            birthYearStr = "1995",
            weightStr = "75.0",
            heightStr = "178.0",
            selectedGender = "זכר",
            themePreference = "light",
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
            onBirthYearChange = {}, onWeightChange = {}, onHeightChange = {}, onGenderSelect = {},
            onThemeSelect = {}, onPrimaryGoalSelect = {}, onActivityLevelSelect = {}, onFocusAreaToggle = {},
            onCaloriesOverrideChange = {}, onStepsOverrideChange = {}, onProteinOverrideChange = {},
            onWaterOverrideChange = {}, onSleepOverrideChange = {}, onSaveClick = {}, onBackClick = {},
            quickActionsEnabled = true, onQuickActionsEnabledChange = {},
            accountState = AccountState.Idle,
            onLogoutClick = {}, onDeleteAccountConfirm = {}
        )
    }
}
