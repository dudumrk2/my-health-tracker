package com.myhealthtracker.app.ui.body

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
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
            viewModel.reset()
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
    val formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("הוספת מדידה", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "סגור",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Summary Header Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "עדכון מדדי גוף",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "הזן את המדידות העדכניות שלך למעקב מדויק",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Date Section Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "תאריך המדידה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 3. Two Column Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Inputs (Waist, Hip, Weight)
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Waist Input
                    MeasurementInputField(
                        value = waistStr,
                        onValueChange = onWaistChange,
                        label = "מותניים (ס״מ)",
                        icon = Icons.Default.Straighten,
                        placeholder = "00"
                    )

                    // Hips Input
                    MeasurementInputField(
                        value = hipsStr,
                        onValueChange = onHipsChange,
                        label = "ירכיים (ס״מ)",
                        icon = Icons.Default.Accessibility,
                        placeholder = "00"
                    )

                    // Weight Input
                    MeasurementInputField(
                        value = weightStr,
                        onValueChange = onWeightChange,
                        label = "משקל (ק״ג)",
                        icon = Icons.Default.MonitorWeight,
                        placeholder = "00.0"
                    )
                }

                // Right Column: Silhouette illustration
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    BodySilhouette(
                        modifier = Modifier.fillMaxSize(),
                        highlightWaist = waistStr.isNotEmpty(),
                        highlightHips = hipsStr.isNotEmpty()
                    )
                }
            }

            // Note Input
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("הערה אישית (אופציונלי)", style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text("למשל: נמדד אחרי אימון") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 4. Tip Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFA0).copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFDFA0).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFF5C4300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "טיפ: כדאי לבצע את המדידה בבוקר, מיד לאחר היקיצה ובמצב צום לקבלת התוצאות המדויקות ביותר.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
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
                    .height(52.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("שמירה", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BodySilhouette(
    modifier: Modifier = Modifier,
    highlightWaist: Boolean = false,
    highlightHips: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val imageRes = if (isDark) {
        com.myhealthtracker.app.R.drawable.body_silhouette_dark
    } else {
        com.myhealthtracker.app.R.drawable.body_silhouette_light
    }

    Image(
        painter = painterResource(id = imageRes),
        contentDescription = "Body Silhouette",
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
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
