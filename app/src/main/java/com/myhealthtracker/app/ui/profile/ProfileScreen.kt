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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.theme.MyHealthTrackerTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val calculatedAge by viewModel.calculatedAge.collectAsState()

    var birthYearStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var heightStr by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }
    var themePreference by remember { mutableStateOf("system") }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Loaded) {
            val profile = (uiState as ProfileUiState.Loaded).profile
            birthYearStr = if (profile.birthYear > 0) profile.birthYear.toString() else ""
            weightStr = if (profile.weightKg > 0.0) profile.weightKg.toString() else ""
            heightStr = if (profile.heightCm > 0.0) profile.heightCm.toString() else ""
            selectedGender = profile.gender
            themePreference = profile.themePreference
            viewModel.updateAge(profile.birthYear)
        } else if (uiState is ProfileUiState.Saved) {
            viewModel.resetState()
            onSaveSuccess()
        }
    }

    ProfileScreenContent(
        uiState = uiState,
        birthYearStr = birthYearStr,
        weightStr = weightStr,
        heightStr = heightStr,
        selectedGender = selectedGender,
        themePreference = themePreference,
        calculatedAge = calculatedAge,
        onBirthYearChange = {
            birthYearStr = it
            it.toIntOrNull()?.let { year -> viewModel.updateAge(year) }
        },
        onWeightChange = { weightStr = it },
        onHeightChange = { heightStr = it },
        onGenderSelect = { selectedGender = it },
        onThemeSelect = { themePreference = it },
        onSaveClick = {
            viewModel.saveProfile(birthYearStr, weightStr, heightStr, selectedGender, themePreference)
        },
        onBackClick = {
            viewModel.resetState()
            onSaveSuccess()
        },
        modifier = modifier
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
    calculatedAge: Int,
    onBirthYearChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onGenderSelect: (String) -> Unit,
    onThemeSelect: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
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
            // Back Button (Top Left)
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text("ביטול", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "בוא נכיר אותך",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
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

            // Card Container for Form fields
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Birth Year Field
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
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Gender Field Selection
                    Column {
                        Text(
                            text = "מין",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val genders = listOf("זכר", "נקבה")
                            genders.forEach { genderOption ->
                                val isSelected = selectedGender == genderOption
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.5f
                                            )
                                        )
                                        .clickable { onGenderSelect(genderOption) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genderOption,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Weight Field
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = onWeightChange,
                        label = { Text("משקל (ק״ג)") },
                        placeholder = { Text("לדוגמה: 75.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Height Field
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = onHeightChange,
                        label = { Text("גובה (ס״מ)") },
                        placeholder = { Text("לדוגמה: 178") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Theme Preference Selection
                    Column {
                        Text(
                            text = "העדפת תצוגה",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themes = listOf(
                                "system" to "מערכת",
                                "light" to "בהירה",
                                "dark" to "כהה"
                            )
                            themes.forEach { (themeValue, themeLabel) ->
                                val isSelected = themePreference == themeValue
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.5f
                                            )
                                        )
                                        .clickable { onThemeSelect(themeValue) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = themeLabel,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error Display
            if (uiState is ProfileUiState.Error) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Save Button
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
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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
            calculatedAge = 31,
            onBirthYearChange = {},
            onWeightChange = {},
            onHeightChange = {},
            onGenderSelect = {},
            onThemeSelect = {},
            onSaveClick = {},
            onBackClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun ProfileScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        ProfileScreenContent(
            uiState = ProfileUiState.Idle,
            birthYearStr = "1995",
            weightStr = "75.0",
            heightStr = "178.0",
            selectedGender = "נקבה",
            themePreference = "dark",
            calculatedAge = 31,
            onBirthYearChange = {},
            onWeightChange = {},
            onHeightChange = {},
            onGenderSelect = {},
            onThemeSelect = {},
            onSaveClick = {},
            onBackClick = {}
        )
    }
}
