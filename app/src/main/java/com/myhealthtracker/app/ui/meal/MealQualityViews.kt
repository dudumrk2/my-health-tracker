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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myhealthtracker.app.R
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
                text = stringResource(R.string.food_quality_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Processed-food score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.food_quality_processing_level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val score = quality.processedScore
                val scoreText = when (score) {
                    1 -> stringResource(R.string.food_quality_score_1)
                    2 -> stringResource(R.string.food_quality_score_2)
                    3 -> stringResource(R.string.food_quality_score_3)
                    4 -> stringResource(R.string.food_quality_score_4)
                    else -> stringResource(R.string.food_quality_score_5)
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
                    text = stringResource(R.string.food_quality_insulin_impact),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val impact = quality.insulinImpact
                val impactText = when (impact) {
                    "low" -> stringResource(R.string.food_quality_impact_low)
                    "medium" -> stringResource(R.string.food_quality_impact_medium)
                    else -> stringResource(R.string.food_quality_impact_high)
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
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.food_quality_complex_carbs)) })
                }
                if (quality.hasSimpleCarbs) {
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.food_quality_simple_carbs)) })
                }
                if (quality.hasHealthyFats) {
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.food_quality_healthy_fats)) })
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
                    text = stringResource(R.string.food_quality_recommendation_title),
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
