package com.myhealthtracker.app.ui.meal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.meal.MealAnalysisException
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object Loading : AddMealStep()
    object ResultState : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analyzer: MealAnalyzer = AppContainer.mealAnalyzer
) : ViewModel() {

    private val _step = MutableStateFlow<AddMealStep>(AddMealStep.InputSelection)
    val step: StateFlow<AddMealStep> = _step.asStateFlow()

    private val _mealDescription = MutableStateFlow("")
    val mealDescription: StateFlow<String> = _mealDescription.asStateFlow()

    private val _recognizedItems = MutableStateFlow<List<MealItem>>(emptyList())
    val recognizedItems: StateFlow<List<MealItem>> = _recognizedItems.asStateFlow()

    private val _excludedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val excludedIndices: StateFlow<Set<Int>> = _excludedIndices.asStateFlow()

    private val _lowConfidence = MutableStateFlow(false)
    val lowConfidence: StateFlow<Boolean> = _lowConfidence.asStateFlow()

    private val _recommendation = MutableStateFlow<String?>(null)
    val recommendation: StateFlow<String?> = _recommendation.asStateFlow()

    private val _quality = MutableStateFlow<MealQuality?>(null)
    val quality: StateFlow<MealQuality?> = _quality.asStateFlow()

    private val _manualCal = MutableStateFlow("")
    val manualCal: StateFlow<String> = _manualCal.asStateFlow()
    private val _manualProtein = MutableStateFlow("")
    val manualProtein: StateFlow<String> = _manualProtein.asStateFlow()
    private val _manualCarbs = MutableStateFlow("")
    val manualCarbs: StateFlow<String> = _manualCarbs.asStateFlow()
    private val _manualFat = MutableStateFlow("")
    val manualFat: StateFlow<String> = _manualFat.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    // "text" or "image" of the last successful analysis, used when saving.
    private var lastInputType: String = "text"

    fun onDescriptionChange(desc: String) {
        _mealDescription.value = desc
        _errorMessage.value = null
    }

    fun onManualCalChange(v: String) { _manualCal.value = v }
    fun onManualProteinChange(v: String) { _manualProtein.value = v }
    fun onManualCarbsChange(v: String) { _manualCarbs.value = v }
    fun onManualFatChange(v: String) { _manualFat.value = v }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun analyzeText() {
        val desc = _mealDescription.value.trim()
        if (desc.isEmpty()) {
            _errorMessage.value = "אנא הקלד תיאור של הארוחה"
            return
        }
        runAnalysis(inputType = "text", text = desc, imageBase64 = null)
    }

    fun analyzeImage(imageBase64: String) {
        runAnalysis(inputType = "image", text = null, imageBase64 = imageBase64)
    }

    fun analyzeImageUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _errorMessage.value = null
            _step.value = AddMealStep.Loading
            val base64 = withContext(Dispatchers.IO) {
                com.myhealthtracker.app.util.ImageEncoder.uriToBase64Jpeg(context, uri)
            }
            if (base64 == null) {
                _errorMessage.value = "שגיאה בעיבוד התמונה"
                _step.value = AddMealStep.InputSelection
                return@launch
            }
            try {
                val result = analyzer.analyze("image", null, base64, today())
                lastInputType = "image"
                _recognizedItems.value = result.items
                _excludedIndices.value = emptySet()
                _lowConfidence.value = result.lowConfidence
                _recommendation.value = result.recommendation
                _quality.value = result.quality
                _step.value = AddMealStep.ResultState
            } catch (e: MealAnalysisException) {
                _errorMessage.value = e.message
                _step.value = AddMealStep.InputSelection
            }
        }
    }

    private fun runAnalysis(inputType: String, text: String?, imageBase64: String?) {
        viewModelScope.launch {
            _errorMessage.value = null
            _step.value = AddMealStep.Loading
            try {
                val result = analyzer.analyze(inputType, text, imageBase64, today())
                lastInputType = inputType
                _recognizedItems.value = result.items
                _excludedIndices.value = emptySet()
                _lowConfidence.value = result.lowConfidence
                _recommendation.value = result.recommendation
                _quality.value = result.quality
                _step.value = AddMealStep.ResultState
            } catch (e: MealAnalysisException) {
                _errorMessage.value = e.message
                _step.value = AddMealStep.InputSelection
            }
        }
    }

    fun updateItem(index: Int, updatedItem: MealItem) {
        val list = _recognizedItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = updatedItem
            _recognizedItems.value = list
        }
    }

    fun toggleItemRemoved(index: Int) {
        if (index !in _recognizedItems.value.indices) return
        val current = _excludedIndices.value
        _excludedIndices.value = if (index in current) current - index else current + index
    }

    fun addItem(item: MealItem): Int {
        val list = _recognizedItems.value.toMutableList()
        list.add(item)
        _recognizedItems.value = list
        return list.size - 1
    }

    fun saveMeal() {
        viewModelScope.launch {
            val manual = _step.value == AddMealStep.ManualFallback
            val description = if (manual) {
                _mealDescription.value.ifEmpty { "ארוחה ידנית" }
            } else {
                _mealDescription.value.ifEmpty { "ארוחה מנותחת AI" }
            }

            val items: List<MealItem>
            val totals: MealTotals
            if (manual) {
                val cal = _manualCal.value.toIntOrNull() ?: 0
                if (cal <= 0) {
                    _errorMessage.value = "הקלוריות חייבות להיות גדולות מ-0"
                    return@launch
                }
                val protein = _manualProtein.value.toIntOrNull() ?: 0
                val carbs = _manualCarbs.value.toIntOrNull() ?: 0
                val fat = _manualFat.value.toIntOrNull() ?: 0
                items = listOf(MealItem(description, "1 מנה", cal, protein, carbs, fat))
                totals = MealTotals(cal, protein, carbs, fat)
            } else {
                val activeItems = _recognizedItems.value
                    .filterIndexed { i, _ -> i !in _excludedIndices.value }
                if (activeItems.isEmpty()) {
                    _errorMessage.value = "כל הפריטים הוסרו"
                    return@launch
                }
                items = activeItems
                totals = MealTotals(
                    calories = activeItems.sumOf { it.calories },
                    proteinG = activeItems.sumOf { it.proteinG },
                    carbsG = activeItems.sumOf { it.carbsG },
                    fatG = activeItems.sumOf { it.fatG }
                )
            }

            mealRepository.addMeal(
                date = today(),
                inputType = if (manual) "text" else lastInputType,
                description = description,
                items = items,
                totals = totals,
                recommendation = if (manual) null else _recommendation.value,
                quality = if (manual) null else _quality.value
            )
            _isSaved.value = true
        }
    }

    fun switchToManualFallback() {
        _errorMessage.value = null
        _step.value = AddMealStep.ManualFallback
        _recommendation.value = null
        _quality.value = null
        _excludedIndices.value = emptySet()
    }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
        _recommendation.value = null
        _quality.value = null
        _excludedIndices.value = emptySet()
    }
}
