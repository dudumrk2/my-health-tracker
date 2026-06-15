package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.theme.CarbsColor
import com.myhealthtracker.app.theme.FatColor
import com.myhealthtracker.app.theme.TealLight

/**
 * AI nutritional-quality card (processed-food score, insulin impact, carb/fat chips).
 * Shared between the meal-analysis result screen and the saved-meal detail sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealQualityCard(quality: MealQuality, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "איכות תזונתית (AI)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Processed-food score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "רמת עיבוד מזון:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val score = quality.processedScore
                val scoreText = when (score) {
                    1 -> "לא מעובד כלל 🥬"
                    2 -> "מעובד מינימלית 🍎"
                    3 -> "מעובד 🍞"
                    4 -> "מעובד מאוד 🍕"
                    else -> "אולטרה-מעובד 🍩"
                }
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (score <= 2) TealLight else if (score == 3) CarbsColor else FatColor
                )
            }

            // Insulin impact
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "השפעה על אינסולין:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val impact = quality.insulinImpact
                val impactText = when (impact) {
                    "low" -> "נמוכה 🟢"
                    "medium" -> "בינונית 🟡"
                    else -> "גבוהה 🔴"
                }
                Text(
                    text = impactText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = when (impact) {
                        "low" -> TealLight
                        "medium" -> CarbsColor
                        else -> FatColor
                    }
                )
            }

            // Quality chips — FlowRow so they wrap instead of clipping on narrow screens
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (quality.hasComplexCarbs) {
                    SuggestionChip(onClick = {}, label = { Text("פחמימות מורכבות ✅") })
                }
                if (quality.hasSimpleCarbs) {
                    SuggestionChip(onClick = {}, label = { Text("פחמימות פשוטות ⚠️") })
                }
                if (quality.hasHealthyFats) {
                    SuggestionChip(onClick = {}, label = { Text("שומנים בריאים 🥑") })
                }
            }
        }
    }
}

/**
 * AI meal-upgrade recommendation card. Shared between the result screen and the
 * saved-meal detail sheet. Callers should only render it for a non-empty string.
 */
@Composable
fun MealRecommendationCard(recommendation: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "המלצת שדרוג AI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
