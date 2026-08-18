package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
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
    val manualCal by viewModel.manualCal.collectAsState()
    val manualProtein by viewModel.manualProtein.collectAsState()
    val manualCarbs by viewModel.manualCarbs.collectAsState()
    val manualFat by viewModel.manualFat.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val closeScreen by viewModel.closeScreen.collectAsState()
    val pendingImagePath by viewModel.pendingImagePath.collectAsState()
    val imageNote by viewModel.imageNote.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) scope.launch {
            com.myhealthtracker.app.util.MealImageStore.saveFromUri(context.applicationContext, uri)
                ?.let { viewModel.prepareImagePath(it) }
        }
        if (!success) pendingCameraFile?.delete()
        pendingCameraFile = null; pendingCameraUri = null
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Re-trigger the camera logic if granted
            val file = createCameraImageFile(context)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "com.myhealthtracker.app.fileprovider", file
            )
            pendingCameraFile = file
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            // Handle permission denied
            android.util.Log.e("AddMealScreen", "Camera permission denied")
        }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            com.myhealthtracker.app.util.MealImageStore.saveFromUri(context, uri)
                ?.let { viewModel.prepareImagePath(it) }
        }
    }

    LaunchedEffect(closeScreen) {
        if (closeScreen) { onDismiss(); viewModel.reset() }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("הוספת ארוחה", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "סגור",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        Spacer(modifier = Modifier.width(48.dp))
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
            AddMealStep.ImagePreview -> {
                ImagePreviewContent(
                    imagePath = pendingImagePath,
                    note = imageNote,
                    errorMessage = errorMessage,
                    onNoteChange = { viewModel.onImageNoteChange(it) },
                    onSendClick = { viewModel.sendImageForAnalysis() },
                    onCancelClick = { viewModel.cancelImagePreview() },
                    modifier = contentModifier
                )
            }
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
                        val cameraPermission = android.Manifest.permission.CAMERA
                        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, cameraPermission
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (isGranted) {
                            val file = createCameraImageFile(context)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "com.myhealthtracker.app.fileprovider", file
                            )
                            pendingCameraFile = file
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(cameraPermission)
                        }
                    },
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
                    onSaveClick = { viewModel.saveManualMeal() },
                    onBackToAiClick = { viewModel.resetToInput() },
                    modifier = contentModifier
                )
            }
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Text Input Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "מה אכלת?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = mealDescription,
                        onValueChange = onDescriptionChange,
                        placeholder = { Text("תאר את הארוחה בפירוט... (למשל: סלט חזה עוף עם רוטב טחינה)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = onAnalyzeTextClick,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("שלח לניתוח AI 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Image upload Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "או צלם תמונה של הארוחה",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCameraClick,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("📷 צילום", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onPickImageClick,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("🖼️ גלריה", fontWeight = FontWeight.Bold)
                        }
                    }
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

            // Manual Entry Fallback Link
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "הזנה ידנית (ללא AI)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clickable { onManualClick() }
                        .padding(8.dp)
                )
            }
        }
    }
}

// 1b. Image Preview Step — show the chosen photo + optional note before AI analysis
@Composable
private fun ImagePreviewContent(
    imagePath: String?,
    note: String,
    errorMessage: String?,
    onNoteChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "תצוגה מקדימה",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            if (imagePath != null) {
                AsyncImage(
                    model = java.io.File(imagePath),
                    contentDescription = "תצוגה מקדימה של הארוחה",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) onNoteChange(it) },
                placeholder = { Text("משהו שכדאי לדעת על המנה? (אופציונלי)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

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
                onClick = onSendClick,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("שלח לניתוח AI 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            TextButton(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("חזרה", fontWeight = FontWeight.Bold)
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Calories
            OutlinedTextField(
                value = cal,
                onValueChange = onCalChange,
                label = { Text("קלוריות (קק״ל)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                // Carbs
                OutlinedTextField(
                    value = carbs,
                    onValueChange = onCarbsChange,
                    label = { Text("פחמימות (ג׳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                // Fat
                OutlinedTextField(
                    value = fat,
                    onValueChange = onFatChange,
                    label = { Text("שומן (ג׳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("שמירה", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onBackToAiClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("חזור לניתוח AI")
            }
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

@Preview(showBackground = true, name = "Image Preview Step")
@Composable
fun AddMealScreenPreviewImagePreview() {
    MyHealthTrackerTheme {
        ImagePreviewContent(
            imagePath = null,
            note = "עם רוטב טחינה",
            errorMessage = null,
            onNoteChange = {},
            onSendClick = {},
            onCancelClick = {}
        )
    }
}

private fun createCameraImageFile(context: android.content.Context): java.io.File {
    val dir = java.io.File(context.cacheDir, "meal_images").apply { mkdirs() }
    return java.io.File.createTempFile("meal_", ".jpg", dir)
}
