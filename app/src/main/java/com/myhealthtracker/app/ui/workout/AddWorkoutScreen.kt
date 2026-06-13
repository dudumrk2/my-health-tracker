package com.myhealthtracker.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AddWorkoutViewModel = viewModel()
    val selectedType by viewModel.selectedType.collectAsState()
    val durationStr by viewModel.durationStr.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onDismiss()
        }
    }

    AddWorkoutContent(
        selectedType = selectedType,
        durationStr = durationStr,
        errorMessage = errorMessage,
        onTypeSelect = { viewModel.selectType(it) },
        onDurationChange = { viewModel.onDurationChange(it) },
        onSaveClick = { viewModel.saveWorkout() },
        onCloseClick = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorkoutContent(
    selectedType: String?,
    durationStr: String,
    errorMessage: String?,
    onTypeSelect: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workoutTypes = listOf(
        Pair("ריצה", "🏃"),
        Pair("הליכה", "🚶"),
        Pair("רכיבה", "🚴"),
        Pair("שחייה", "🏊"),
        Pair("כוח", "🏋️"),
        Pair("יוגה", "🧘"),
        Pair("אחר", "💪")
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("הוספת אימון", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Text("❌", fontSize = 16.sp) // Close button (RTL)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Grid of Workout Types
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "בחר סוג אימון",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                ) {
                    items(workoutTypes) { (typeName, icon) ->
                        val isSelected = selectedType == typeName
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onTypeSelect(typeName) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = typeName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. Input Fields (Duration)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "פרטי האימון",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedTextField(
                    value = durationStr,
                    onValueChange = onDurationChange,
                    label = { Text("משך זמן (בדקות)") },
                    placeholder = { Text("הזן בדקות, לדוגמה: 45") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // 3. Time Picker Info (Static default)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("זמן אימון", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} | ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text("⏰", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Error Display
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Save Button
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
                Text("שמירה", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun AddWorkoutScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        AddWorkoutContent(
            selectedType = "ריצה",
            durationStr = "30",
            errorMessage = null,
            onTypeSelect = {},
            onDurationChange = {},
            onSaveClick = {},
            onCloseClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun AddWorkoutScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        AddWorkoutContent(
            selectedType = null,
            durationStr = "",
            errorMessage = "אנא הזן משך זמן אימון תקין",
            onTypeSelect = {},
            onDurationChange = {},
            onSaveClick = {},
            onCloseClick = {}
        )
    }
}
