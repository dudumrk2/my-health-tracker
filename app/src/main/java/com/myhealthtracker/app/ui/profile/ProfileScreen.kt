package com.myhealthtracker.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Loaded) {
            val profile = (uiState as ProfileUiState.Loaded).profile
            birthYearStr = if (profile.birthYear > 0) profile.birthYear.toString() else ""
            weightStr = if (profile.weightKg > 0.0) profile.weightKg.toString() else ""
            heightStr = if (profile.heightCm > 0.0) profile.heightCm.toString() else ""
            viewModel.updateAge(profile.birthYear)
        } else if (uiState is ProfileUiState.Saved) {
            onSaveSuccess()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Profile Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = birthYearStr,
            onValueChange = {
                birthYearStr = it
                it.toIntOrNull()?.let { year -> viewModel.updateAge(year) }
            },
            label = { Text("Birth Year") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        if (calculatedAge > 0) {
            Text(
                text = "Calculated Age: $calculatedAge",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        OutlinedTextField(
            value = weightStr,
            onValueChange = { weightStr = it },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = heightStr,
            onValueChange = { heightStr = it },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        )

        if (uiState is ProfileUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    viewModel.saveProfile(birthYearStr, weightStr, heightStr)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save Profile")
            }
        }

        if (uiState is ProfileUiState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (uiState as ProfileUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
