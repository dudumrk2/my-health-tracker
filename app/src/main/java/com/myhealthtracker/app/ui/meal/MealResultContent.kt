package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.theme.CarbsColor
import com.myhealthtracker.app.theme.ProteinColor

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

/**
 * Reusable meal result/editor composable. Reproduces the former AddMealScreen's
 * ResultStateContent layout with two changes:
 *   - Accepts [imagePath] and renders it as the first list item when non-null.
 *   - Does NOT include the "הזנה ידנית" manual-entry link.
 *
 * Totals derive from active (non-excluded) items only.
 */
@Composable
fun MealResultContent(
    imagePath: String?,
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
            // Image item — shown only when imagePath is provided (delta a)
            if (imagePath != null) {
                item {
                    AsyncImage(
                        model = java.io.File(imagePath),
                        contentDescription = "תמונת הארוחה",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
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
                        // Static Item View
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
                                        item.name.contains("אספרגוס") -> "4g"
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

            // Action Buttons — no "הזנה ידנית" link (delta b)
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
