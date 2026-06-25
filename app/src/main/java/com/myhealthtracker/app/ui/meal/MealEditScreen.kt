package com.myhealthtracker.app.ui.meal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.data.model.MealEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(
    meal: MealEntry,
    celebrateOnOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: MealEditViewModel = viewModel()
    LaunchedEffect(meal.mealId) {
        viewModel.load(meal)
        if (celebrateOnOpen) viewModel.celebrateQuality()
    }
    val items by viewModel.recognizedItems.collectAsState()
    val excluded by viewModel.excludedIndices.collectAsState()
    val recommendation by viewModel.recommendation.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val imagePath by viewModel.imagePath.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) { if (saved) onDismiss() }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("פרטי הארוחה", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "סגור") } }
            )
        }
    ) { padding ->
        MealResultContent(
            imagePath = imagePath,
            recognizedItems = items,
            excludedIndices = excluded,
            lowConfidence = false,
            recommendation = recommendation,
            quality = quality,
            errorMessage = error,
            onItemUpdate = { i, it -> viewModel.updateItem(i, it) },
            onToggleRemoved = { viewModel.toggleItemRemoved(it) },
            onItemAdd = { viewModel.addItem(it) },
            onSaveClick = { viewModel.save() },
            modifier = Modifier.padding(padding)
        )
    }
}
