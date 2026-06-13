package com.myhealthtracker.app.ui.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBodyMeasurementScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AddBodyMeasurementViewModel = viewModel()
    val weightStr by viewModel.weightStr.collectAsState()
    val waistStr by viewModel.waistStr.collectAsState()
    val hipsStr by viewModel.hipsStr.collectAsState()
    val note by viewModel.note.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onDismiss()
        }
    }

    AddBodyMeasurementContent(
        weightStr = weightStr,
        waistStr = waistStr,
        hipsStr = hipsStr,
        note = note,
        errorMessage = errorMessage,
        onWeightChange = { viewModel.onWeightChange(it) },
        onWaistChange = { viewModel.onWaistChange(it) },
        onHipsChange = { viewModel.onHipsChange(it) },
        onNoteChange = { viewModel.onNoteChange(it) },
        onSaveClick = { viewModel.saveMeasurement() },
        onCloseClick = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBodyMeasurementContent(
    weightStr: String,
    waistStr: String,
    hipsStr: String,
    note: String,
    errorMessage: String?,
    onWeightChange: (String) -> Unit,
    onWaistChange: (String) -> Unit,
    onHipsChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("הוספת מדדי גוף", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Text("❌", fontSize = 16.sp) // Close (RTL)
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
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Date Information Header
            Text(
                text = "מדידה לתאריך: $formattedDate",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 2. Custom Drawn Body Silhouette
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Silhouette Drawing
                    BodySilhouette(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        highlightWaist = waistStr.isNotEmpty(),
                        highlightHips = hipsStr.isNotEmpty()
                    )

                    // Guidelines in Hebrew
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "נקודות מדידה:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🟢", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text("מותניים: בנקודה הצרה", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔵", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text("ירכיים: בנקודה הרחבה", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚖️", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text("משקל: בבוקר על ריק", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. Form Fields Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weight Input
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = onWeightChange,
                        label = { Text("משקל גוף (ק״ג)") },
                        placeholder = { Text("לדוגמה: 75.2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Waist Input
                    OutlinedTextField(
                        value = waistStr,
                        onValueChange = onWaistChange,
                        label = { Text("היקף מותן (ס״מ)") },
                        placeholder = { Text("לדוגמה: 84.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Hips Input
                    OutlinedTextField(
                        value = hipsStr,
                        onValueChange = onHipsChange,
                        label = { Text("היקף ירכיים (ס״מ)") },
                        placeholder = { Text("לדוגמה: 96.2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Note Input
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        label = { Text("הערה אישית (אופציונלי)") },
                        placeholder = { Text("למשל: נמדד אחרי אימון") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BodySilhouette(
    modifier: Modifier = Modifier,
    highlightWaist: Boolean = false,
    highlightHips: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryVariant = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val indicatorWaistColor = if (highlightWaist) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
    val indicatorHipsColor = if (highlightHips) MaterialTheme.colorScheme.primary else Color(0xFF2196F3)
    val scaleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f

        // Head
        val headRadius = 14.dp.toPx()
        val headCenterY = 24.dp.toPx()
        drawCircle(
            color = primaryVariant,
            radius = headRadius,
            center = Offset(centerX, headCenterY)
        )

        // Neck
        val neckWidth = 6.dp.toPx()
        val neckHeight = 8.dp.toPx()
        val neckTopY = headCenterY + headRadius
        drawRect(
            color = primaryVariant,
            topLeft = Offset(centerX - neckWidth / 2, neckTopY),
            size = androidx.compose.ui.geometry.Size(neckWidth, neckHeight)
        )

        // Torso
        val shoulderWidth = 40.dp.toPx()
        val torsoWidth = 30.dp.toPx()
        val torsoHeight = 65.dp.toPx()
        val torsoTopY = neckTopY + neckHeight

        val torsoPath = Path().apply {
            moveTo(centerX - shoulderWidth / 2, torsoTopY)
            lineTo(centerX + shoulderWidth / 2, torsoTopY)
            lineTo(centerX + torsoWidth / 2, torsoTopY + torsoHeight)
            lineTo(centerX - torsoWidth / 2, torsoTopY + torsoHeight)
            close()
        }
        drawPath(
            path = torsoPath,
            color = primaryVariant
        )

        // Legs
        val legWidth = 10.dp.toPx()
        val legHeight = 70.dp.toPx()
        val legTopY = torsoTopY + torsoHeight
        val legSpacing = 4.dp.toPx()

        drawRoundRect(
            color = primaryVariant,
            topLeft = Offset(centerX - legWidth - legSpacing / 2, legTopY),
            size = androidx.compose.ui.geometry.Size(legWidth, legHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = primaryVariant,
            topLeft = Offset(centerX + legSpacing / 2, legTopY),
            size = androidx.compose.ui.geometry.Size(legWidth, legHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )

        // Arms
        val armWidth = 8.dp.toPx()
        val armHeight = 65.dp.toPx()
        drawRoundRect(
            color = primaryVariant,
            topLeft = Offset(centerX - shoulderWidth / 2 - armWidth - 2.dp.toPx(), torsoTopY + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(armWidth, armHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = primaryVariant,
            topLeft = Offset(centerX + shoulderWidth / 2 + 2.dp.toPx(), torsoTopY + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(armWidth, armHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )

        // Scale Base (under feet)
        val scaleWidth = 60.dp.toPx()
        val scaleHeight = 6.dp.toPx()
        val scaleTopY = legTopY + legHeight
        drawRoundRect(
            color = scaleColor,
            topLeft = Offset(centerX - scaleWidth / 2, scaleTopY),
            size = androidx.compose.ui.geometry.Size(scaleWidth, scaleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )

        // Waist line
        val waistY = torsoTopY + torsoHeight * 0.5f
        drawLine(
            color = indicatorWaistColor,
            start = Offset(centerX - torsoWidth / 2 - 10.dp.toPx(), waistY),
            end = Offset(centerX + torsoWidth / 2 + 10.dp.toPx(), waistY),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = indicatorWaistColor,
            radius = 4.dp.toPx(),
            center = Offset(centerX + torsoWidth / 2 + 10.dp.toPx(), waistY)
        )

        // Hips line
        val hipsY = torsoTopY + torsoHeight * 0.85f
        drawLine(
            color = indicatorHipsColor,
            start = Offset(centerX - torsoWidth / 2 - 14.dp.toPx(), hipsY),
            end = Offset(centerX + torsoWidth / 2 + 14.dp.toPx(), hipsY),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = indicatorHipsColor,
            radius = 4.dp.toPx(),
            center = Offset(centerX + torsoWidth / 2 + 14.dp.toPx(), hipsY)
        )
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun AddBodyMeasurementScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        AddBodyMeasurementContent(
            weightStr = "75.2",
            waistStr = "84.5",
            hipsStr = "",
            note = "",
            errorMessage = null,
            onWeightChange = {},
            onWaistChange = {},
            onHipsChange = {},
            onNoteChange = {},
            onSaveClick = {},
            onCloseClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun AddBodyMeasurementScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        AddBodyMeasurementContent(
            weightStr = "",
            waistStr = "",
            hipsStr = "",
            note = "נמדד בערב",
            errorMessage = "היקף הירכיים אינו תקין",
            onWeightChange = {},
            onWaistChange = {},
            onHipsChange = {},
            onNoteChange = {},
            onSaveClick = {},
            onCloseClick = {}
        )
    }
}
