package com.myhealthtracker.app.ui.meal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
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
    object ImagePreview : AddMealStep()
    object Loading : AddMealStep()
    object ResultState : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analyzer: MealAnalyzer = AppContainer.mealAnalyzer,
    private val celebrationController: CelebrationController = AppContainer.celebrationController
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

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    private val _imageNote = MutableStateFlow("")
    val imageNote: StateFlow<String> = _imageNote.asStateFlow()

    // Held only during the ImagePreview step; cleared on send/cancel.
    private var pendingImageBase64: String? = null

    fun onImageNoteChange(note: String) { _imageNote.value = note }

    // Test seam: lets unit tests stage a pending image without an Android Uri/encode.
    internal fun seedPendingImageForTest(base64: String) {
        pendingImageBase64 = base64
        _step.value = AddMealStep.ImagePreview
    }

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

    fun prepareImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _errorMessage.value = null
            _imageNote.value = ""
            _step.value = AddMealStep.Loading
            val base64 = withContext(Dispatchers.IO) {
                com.myhealthtracker.app.util.ImageEncoder.uriToBase64Jpeg(context, uri)
            }
            if (base64 == null) {
                _errorMessage.value = "שגיאה בעיבוד התמונה"
                _step.value = AddMealStep.InputSelection
                return@launch
            }
            pendingImageBase64 = base64
            _pendingImageUri.value = uri
            _step.value = AddMealStep.ImagePreview
        }
    }

    fun sendImageForAnalysis() {
        val base64 = pendingImageBase64 ?: return
        val note = _imageNote.value.trim()
        if (note.isNotEmpty()) {
            _mealDescription.value = note
        }
        runAnalysis(inputType = "image", text = note.ifEmpty { null }, imageBase64 = base64)
    }

    fun cancelImagePreview() {
        _errorMessage.value = null
        _imageNote.value = ""
        pendingImageBase64 = null
        _pendingImageUri.value = null
        _step.value = AddMealStep.InputSelection
    }

    private fun runAnalysis(inputType: String, text: String?, imageBase64: String?) {
        viewModelScope.launch {
            _errorMessage.value = null
            _step.value = AddMealStep.Loading
            try {
                val result = analyzer.analyze(inputType, text, imageBase64, today())
                lastInputType = inputType
                pendingImageBase64 = null
                _pendingImageUri.value = null
                _recognizedItems.value = result.items
                _excludedIndices.value = emptySet()
                _lowConfidence.value = result.lowConfidence
                _recommendation.value = result.recommendation
                _quality.value = result.quality
                _step.value = AddMealStep.ResultState
            } catch (e: MealAnalysisException) {
                _errorMessage.value = e.message
                pendingImageBase64 = null
                _pendingImageUri.value = null
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
            // Celebrate a high-quality AI meal (great > good). One-shot at save via
            // celebrateNow, which ignores the event's dedupKey, so each saved AI meal
            // celebrates independently. Manual meals have no AI quality (guarded by !manual).
            if (!manual) {
                celebrationController.celebrateNow(
                    CelebrationRules.mealQuality(_quality.value, today())
                )
            }
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

    /**
     * Restore the initial input state and clear the saved flag. ViewModels here are
     * not scoped per nav entry, so the same instance is reused across openings; without
     * this, a stale `isSaved = true` would make the screen dismiss itself the instant it
     * reopens (the "add meal" button appears dead after the first save).
     */
    fun reset() {
        _step.value = AddMealStep.InputSelection
        _mealDescription.value = ""
        _recognizedItems.value = emptyList()
        _excludedIndices.value = emptySet()
        _lowConfidence.value = false
        _recommendation.value = null
        _quality.value = null
        _manualCal.value = ""
        _manualProtein.value = ""
        _manualCarbs.value = ""
        _manualFat.value = ""
        _errorMessage.value = null
        _isSaved.value = false
        _pendingImageUri.value = null
        _imageNote.value = ""
        pendingImageBase64 = null
        lastInputType = "text"
    }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
        _recommendation.value = null
        _quality.value = null
        _excludedIndices.value = emptySet()
        _imageNote.value = ""
        pendingImageBase64 = null
        _pendingImageUri.value = null
    }
}
