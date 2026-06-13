package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AddMealViewModel = viewModel()
    val step by viewModel.step.collectAsState()
    val mealDescription by viewModel.mealDescription.collectAsState()
    val recognizedItems by viewModel.recognizedItems.collectAsState()
    
    val manualCal by viewModel.manualCal.collectAsState()
    val manualProtein by viewModel.manualProtein.collectAsState()
    val manualCarbs by viewModel.manualCarbs.collectAsState()
    val manualFat by viewModel.manualFat.collectAsState()
    
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val lowConfidence by viewModel.lowConfidence.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.analyzeImageUri(context, uri)
        }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        try {
            if (success && uri != null) {
                viewModel.analyzeImageUri(context.applicationContext, uri)
            }
        } finally {
            file?.delete()
            pendingCameraFile = null
            pendingCameraUri = null
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onDismiss()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("הוספת ארוחה", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Text("❌", fontSize = 16.sp) // Close button (RTL)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)

        when (step) {
            AddMealStep.InputSelection -> {
                InputSelectionContent(
                    mealDescription = mealDescription,
                    errorMessage = errorMessage,
                    onDescriptionChange = { viewModel.onDescriptionChange(it) },
                    onAnalyzeTextClick = { viewModel.analyzeText() },
                    onPickImageClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onCameraClick = {
                        val file = createCameraImageFile(context)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        pendingCameraFile = file
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    onManualClick = { viewModel.switchToManualFallback() },
                    modifier = contentModifier
                )
            }
            AddMealStep.Loading -> {
                LoadingContent(modifier = contentModifier)
            }
            AddMealStep.ResultState -> {
                ResultStateContent(
                    recognizedItems = recognizedItems,
                    lowConfidence = lowConfidence,
                    errorMessage = errorMessage,
                    onItemUpdate = { index, item -> viewModel.updateItem(index, item) },
                    onSaveClick = { viewModel.saveMeal() },
                    onManualClick = { viewModel.switchToManualFallback() },
                    modifier = contentModifier
                )
            }
            AddMealStep.ManualFallback -> {
                ManualFallbackContent(
                    description = mealDescription,
                    cal = manualCal,
                    protein = manualProtein,
                    carbs = manualCarbs,
                    fat = manualFat,
                    errorMessage = errorMessage,
                    onDescriptionChange = { viewModel.onDescriptionChange(it) },
                    onCalChange = { viewModel.onManualCalChange(it) },
                    onProteinChange = { viewModel.onManualProteinChange(it) },
                    onCarbsChange = { viewModel.onManualCarbsChange(it) },
                    onFatChange = { viewModel.onManualFatChange(it) },
                    onSaveClick = { viewModel.saveMeal() },
                    onBackToAiClick = { viewModel.resetToInput() },
                    modifier = contentModifier
                )
            }
        }
    }
}

// 1. Input Selection Step Layout
@Composable
private fun InputSelectionContent(
    mealDescription: String,
    errorMessage: String?,
    onDescriptionChange: (String) -> Unit,
    onAnalyzeTextClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Text Input Options
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "מה אכלת?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = mealDescription,
                onValueChange = onDescriptionChange,
                placeholder = { Text("תאר את הארוחה בפירוט... (למשל: סלט חסה ועוף עם רוטב טחינה)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onAnalyzeTextClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("שלח לניתוח AI 🚀")
            }
        }

        // Image / Photo Upload Box
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "או צלם תמונה של הארוחה",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCameraClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("📷 צילום") }
                OutlinedButton(
                    onClick = onPickImageClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("🖼️ גלריה") }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Error display
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Manual Entry Fallback Button
        TextButton(
            onClick = onManualClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("הזנה ידנית (ללא AI)", fontWeight = FontWeight.Bold)
        }
    }
}

// 2. Loading Step Layout
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "מנתח את הארוחה ב-AI...",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "אנחנו מפרקים את פריטי המזון ומחשבים ערכים תזונתיים",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// 3. Result State Layout (Editable recognized items + totals)
@Composable
private fun ResultStateContent(
    recognizedItems: List<MealItem>,
    lowConfidence: Boolean,
    errorMessage: String?,
    onItemUpdate: (Int, MealItem) -> Unit,
    onSaveClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate totals dynamically from recognized items state
    val totals = MealTotals(
        calories = recognizedItems.sumOf { it.calories },
        proteinG = recognizedItems.sumOf { it.proteinG },
        carbsG = recognizedItems.sumOf { it.carbsG },
        fatG = recognizedItems.sumOf { it.fatG }
    )

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "תוצאות ניתוח ארוחה (ניתן לעריכה)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (lowConfidence) {
            Text(
                text = "⚠️ הזיהוי אינו ודאי — מומלץ לעבור על הכמויות ולערוך לפני שמירה.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
        // List of editable items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(recognizedItems) { index, item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Food Item Name
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { onItemUpdate(index, item.copy(name = it)) },
                            label = { Text("שם המנה") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quantity
                            OutlinedTextField(
                                value = item.quantity,
                                onValueChange = { onItemUpdate(index, item.copy(quantity = it)) },
                                label = { Text("כמות/משקל") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // Calories
                            OutlinedTextField(
                                value = item.calories.toString(),
                                onValueChange = {
                                    val cal = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(calories = cal))
                                },
                                label = { Text("קק״ל") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Protein
                            OutlinedTextField(
                                value = item.proteinG.toString(),
                                onValueChange = {
                                    val prot = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(proteinG = prot))
                                },
                                label = { Text("חלבון") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // Carbs
                            OutlinedTextField(
                                value = item.carbsG.toString(),
                                onValueChange = {
                                    val carb = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(carbsG = carb))
                                },
                                label = { Text("פחמימות") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // Fat
                            OutlinedTextField(
                                value = item.fatG.toString(),
                                onValueChange = {
                                    val fat = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(fatG = fat))
                                },
                                label = { Text("שומן") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // Totals Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "סך הכל לארוחה",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("קלוריות: ${totals.calories} קק״ל", fontWeight = FontWeight.Bold)
                    Text("חלבון: ${totals.proteinG}ג׳", color = ProteinColor, fontWeight = FontWeight.Bold)
                    Text("פחמימות: ${totals.carbsG}ג׳", color = CarbsColor, fontWeight = FontWeight.Bold)
                    Text("שומן: ${totals.fatG}ג׳", color = FatColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSaveClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.5f)
                    .height(56.dp)
            ) {
                Text("שמירה", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onManualClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("הזנה ידנית", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 4. Manual Fallback Step Layout
@Composable
private fun ManualFallbackContent(
    description: String,
    cal: String,
    protein: String,
    carbs: String,
    fat: String,
    errorMessage: String?,
    onDescriptionChange: (String) -> Unit,
    onCalChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackToAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "הזנת ארוחה ידנית",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("תיאור הארוחה") },
            placeholder = { Text("לדוגמה: כריך אבוקדו וביצה קשה") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // Calories
        OutlinedTextField(
            value = cal,
            onValueChange = onCalChange,
            label = { Text("קלוריות (קק״ל)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Protein
            OutlinedTextField(
                value = protein,
                onValueChange = onProteinChange,
                label = { Text("חלבון (ג׳)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
            // Carbs
            OutlinedTextField(
                value = carbs,
                onValueChange = onCarbsChange,
                label = { Text("פחמימות (ג׳)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
            // Fat
            OutlinedTextField(
                value = fat,
                onValueChange = onFatChange,
                label = { Text("שומן (ג׳)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onSaveClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("שמירה", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onBackToAiClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חזור לניתוח AI")
        }
    }
}

@Preview(showBackground = true, name = "Input Selection Step")
@Composable
fun AddMealScreenPreviewInput() {
    MyHealthTrackerTheme {
        InputSelectionContent(
            mealDescription = "סלט חזה עוף מפנק",
            errorMessage = null,
            onDescriptionChange = {},
            onAnalyzeTextClick = {},
            onPickImageClick = {},
            onCameraClick = {},
            onManualClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading Step")
@Composable
fun AddMealScreenPreviewLoading() {
    MyHealthTrackerTheme {
        LoadingContent()
    }
}

@Preview(showBackground = true, name = "Result State Step")
@Composable
fun AddMealScreenPreviewResult() {
    MyHealthTrackerTheme {
        ResultStateContent(
            recognizedItems = listOf(
                MealItem("חזה עוף", "150 גרם", 250, 46, 0, 5),
                MealItem("שמן זית", "1 כף", 120, 0, 0, 14)
            ),
            lowConfidence = false,
            errorMessage = null,
            onItemUpdate = { _, _ -> },
            onSaveClick = {},
            onManualClick = {}
        )
    }
}

private fun createCameraImageFile(context: android.content.Context): java.io.File {
    val dir = java.io.File(context.cacheDir, "meal_images").apply { mkdirs() }
    return java.io.File.createTempFile("meal_", ".jpg", dir)
}
