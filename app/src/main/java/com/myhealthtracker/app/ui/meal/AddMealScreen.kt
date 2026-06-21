package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.theme.*

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Float = 3f,
    dashLength: Float = 15f,
    gapLength: Float = 10f,
    cornerRadius: Float = 24f
) = this.drawBehind {
    val paint = androidx.compose.ui.graphics.Paint().apply {
        this.color = color
        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
        this.strokeWidth = strokeWidth
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
    }
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        )
    }
    drawIntoCanvas { canvas ->
        canvas.drawPath(path, paint)
    }
}

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
    val recommendation by viewModel.recommendation.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val excludedIndices by viewModel.excludedIndices.collectAsState()
    val pendingImageUri by viewModel.pendingImageUri.collectAsState()
    val imageNote by viewModel.imageNote.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        try {
            if (success && uri != null) {
                viewModel.prepareImage(context.applicationContext, uri)
            }
        } finally {
            if (!success) {
                file?.delete()
            }
            pendingCameraFile = null
            pendingCameraUri = null
        }
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
        if (uri != null) {
            viewModel.prepareImage(context, uri)
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onDismiss()
            // The VM isn't scoped per nav entry, so clear it now; otherwise the stale
            // isSaved=true would dismiss the screen instantly the next time it's opened.
            viewModel.reset()
        }
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
                    imageUri = pendingImageUri,
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
            AddMealStep.Loading -> {
                LoadingContent(modifier = contentModifier)
            }
            AddMealStep.ResultState -> {
                ResultStateContent(
                    recognizedItems = recognizedItems,
                    excludedIndices = excludedIndices,
                    lowConfidence = lowConfidence,
                    recommendation = recommendation,
                    quality = quality,
                    errorMessage = errorMessage,
                    onItemUpdate = { index, item -> viewModel.updateItem(index, item) },
                    onToggleRemoved = { index -> viewModel.toggleItemRemoved(index) },
                    onItemAdd = { item -> viewModel.addItem(item) },
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
    imageUri: android.net.Uri?,
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

            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "תצוגה מקדימה של הארוחה",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
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

// 2. Loading Step Layout
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
    excludedIndices: Set<Int>,
    lowConfidence: Boolean,
    recommendation: String?,
    quality: MealQuality?,
    errorMessage: String?,
    onItemUpdate: (Int, MealItem) -> Unit,
    onToggleRemoved: (Int) -> Unit,
    onItemAdd: (MealItem) -> Int,
    onSaveClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    // Active items = not marked removed. Totals derive from these only.
    val activeItems = recognizedItems.filterIndexed { i, _ -> i !in excludedIndices }
    val totals = MealTotals(
        calories = activeItems.sumOf { it.calories },
        proteinG = activeItems.sumOf { it.proteinG },
        carbsG = activeItems.sumOf { it.carbsG },
        fatG = activeItems.sumOf { it.fatG }
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
        // Dotted Camera Box at the top
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 3f, cornerRadius = 24f)
                    .clickable { /* Re-trigger camera */ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "צילום תמונה",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "צילום או העלאת תמונה",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "לניתוח AI אוטומטי של המנה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Macro Summary Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "סה״כ קלוריות",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${totals.calories}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Progress Ring
                    val progress = (totals.calories.toFloat() / 2500f).coerceIn(0f, 1f)
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(68.dp)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Side-by-side protein/carbs summaries
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Carbs
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("פחמימות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${totals.carbsG}g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CarbsColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (totals.carbsG.toFloat() / 250f).coerceIn(0f, 1f) },
                            color = CarbsColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }

                // Protein
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("חלבון", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${totals.proteinG}g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ProteinColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (totals.proteinG.toFloat() / 150f).coerceIn(0f, 1f) },
                            color = ProteinColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }
        }

        // Info Notice Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "הערכה משוערת מבוססת על זיהוי חזותי",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // AI quality and recommendation cards
        if (quality != null) {
            item {
                MealQualityCard(quality = quality)
            }
        }

        if (!recommendation.isNullOrEmpty()) {
            item {
                MealRecommendationCard(recommendation = recommendation)
            }
        }

        // Section Title
        item {
            Text(
                text = "מרכיבי הארוחה",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // List of items
        itemsIndexed(recognizedItems) { index, item ->
            val isRemoved = index in excludedIndices
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isRemoved) Modifier.alpha(0.5f) else Modifier)
            ) {
                if (editingIndex == index) {
                    // Item Editor View
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { onItemUpdate(index, item.copy(name = it)) },
                            label = { Text("שם המנה") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = item.quantity,
                                onValueChange = { onItemUpdate(index, item.copy(quantity = it)) },
                                label = { Text("כמות/משקל") },
                                modifier = Modifier.weight(1.5f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = item.calories.toString(),
                                onValueChange = {
                                    val cal = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(calories = cal))
                                },
                                label = { Text("קק״ל") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = item.proteinG.toString(),
                                onValueChange = {
                                    val prot = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(proteinG = prot))
                                },
                                label = { Text("חלבון") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = item.carbsG.toString(),
                                onValueChange = {
                                    val carb = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(carbsG = carb))
                                },
                                label = { Text("פחמימות") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = item.fatG.toString(),
                                onValueChange = {
                                    val fat = it.toIntOrNull() ?: 0
                                    onItemUpdate(index, item.copy(fatG = fat))
                                },
                                label = { Text("שומן") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )
                        }

                        // Save Item Button
                        Button(
                            onClick = { editingIndex = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("אישור")
                        }
                    }
                } else {
                    // Static Mockup Item View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Emoji Avatar Box
                                val foodEmoji = when {
                                    item.name.contains("סלמון") -> "🍣"
                                    item.name.contains("קינואה") -> "🍛"
                                    item.name.contains("אספרגוס") -> "🥦"
                                    else -> "🥗"
                                }
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(foodEmoji, fontSize = 28.sp)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.name.ifEmpty { "פריט ללא שם" },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = if (isRemoved) TextDecoration.LineThrough else null
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.quantity,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!isRemoved) {
                                    IconButton(
                                        onClick = { editingIndex = index },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "ערוך",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleRemoved(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRemoved) Icons.AutoMirrored.Filled.Undo else Icons.Default.Delete,
                                        contentDescription = if (isRemoved) "שחזר" else "הסר",
                                        tint = if (isRemoved) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )

                        // Bottom Section (Metrics)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Calories Column
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("קלו׳", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${item.calories}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                // Protein Column
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("חלבון", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${item.proteinG}g", style = MaterialTheme.typography.bodyMedium)
                                }
                                // Carbs/Fat/Fiber Column
                                val macroLabel = when {
                                    item.name.contains("אספרגוס") -> "סיבים"
                                    item.name.contains("סלמון") -> "שומן"
                                    else -> "פחמימות"
                                }
                                val macroValue = when {
                                    item.name.contains("אספרגוס") -> "4g" // fibers from mockup
                                    item.name.contains("סלמון") -> "${item.fatG}g"
                                    else -> "${item.carbsG}g"
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(macroLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(macroValue, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "מאושר",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Add Item Dotted Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 2f, cornerRadius = 12f)
                    .clickable {
                        val newItem = MealItem("", "100 גרם", 0, 0, 0, 0)
                        val newIndex = onItemAdd(newItem)
                        editingIndex = newIndex
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "הוספת פריט נוסף",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Action Buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onSaveClick,
                    enabled = activeItems.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("שמירה", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                if (recognizedItems.isNotEmpty() && activeItems.isEmpty()) {
                    Text(
                        text = "כל הפריטים הוסרו",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onManualClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("הזנה ידנית", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            imageUri = null,
            note = "עם רוטב טחינה",
            errorMessage = null,
            onNoteChange = {},
            onSendClick = {},
            onCancelClick = {}
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
            excludedIndices = setOf(1),
            lowConfidence = false,
            recommendation = null,
            quality = null,
            errorMessage = null,
            onItemUpdate = { _, _ -> },
            onToggleRemoved = {},
            onItemAdd = { 0 },
            onSaveClick = {},
            onManualClick = {}
        )
    }
}

private fun createCameraImageFile(context: android.content.Context): java.io.File {
    val dir = java.io.File(context.cacheDir, "meal_images").apply { mkdirs() }
    return java.io.File.createTempFile("meal_", ".jpg", dir)
}
