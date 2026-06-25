package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import com.myhealthtracker.app.data.meal.MealAnalysisLauncher
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object ImagePreview : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val analysisLauncher: MealAnalysisLauncher = AppContainer.mealAnalysisLauncher,
    @Suppress("unused") private val celebrationController: CelebrationController = AppContainer.celebrationController
) : ViewModel() {

    private val _step = MutableStateFlow<AddMealStep>(AddMealStep.InputSelection)
    val step: StateFlow<AddMealStep> = _step.asStateFlow()
    private val _mealDescription = MutableStateFlow("")
    val mealDescription: StateFlow<String> = _mealDescription.asStateFlow()
    private val _imageNote = MutableStateFlow("")
    val imageNote: StateFlow<String> = _imageNote.asStateFlow()
    private val _pendingImagePath = MutableStateFlow<String?>(null)
    val pendingImagePath: StateFlow<String?> = _pendingImagePath.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _manualCal = MutableStateFlow(""); val manualCal: StateFlow<String> = _manualCal.asStateFlow()
    private val _manualProtein = MutableStateFlow(""); val manualProtein: StateFlow<String> = _manualProtein.asStateFlow()
    private val _manualCarbs = MutableStateFlow(""); val manualCarbs: StateFlow<String> = _manualCarbs.asStateFlow()
    private val _manualFat = MutableStateFlow(""); val manualFat: StateFlow<String> = _manualFat.asStateFlow()
    private val _closeScreen = MutableStateFlow(false)
    val closeScreen: StateFlow<Boolean> = _closeScreen.asStateFlow()

    fun onDescriptionChange(desc: String) { _mealDescription.value = desc; _errorMessage.value = null }
    fun onImageNoteChange(note: String) { _imageNote.value = note }
    fun onManualCalChange(v: String) { _manualCal.value = v }
    fun onManualProteinChange(v: String) { _manualProtein.value = v }
    fun onManualCarbsChange(v: String) { _manualCarbs.value = v }
    fun onManualFatChange(v: String) { _manualFat.value = v }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun prepareImagePath(path: String) {
        _errorMessage.value = null
        _imageNote.value = ""
        _pendingImagePath.value = path
        _step.value = AddMealStep.ImagePreview
    }

    internal fun seedPendingImagePathForTest(path: String) = prepareImagePath(path)

    fun analyzeText() {
        val desc = _mealDescription.value.trim()
        if (desc.isEmpty()) { _errorMessage.value = "אנא הקלד תיאור של הארוחה"; return }
        val mealId = mealRepository.newMealId()
        mealRepository.createPendingMeal(mealId, today(), "text", desc, null, null)
        analysisLauncher.launch(MealAnalysisInput(mealId, "text", desc, null, today()))
        _closeScreen.value = true
    }

    fun sendImageForAnalysis() {
        val path = _pendingImagePath.value ?: return
        val note = _imageNote.value.trim().ifEmpty { null }
        val description = note ?: "ארוחה מנותחת AI"
        val mealId = mealRepository.newMealId()
        mealRepository.createPendingMeal(mealId, today(), "image", description, note, path)
        analysisLauncher.launch(MealAnalysisInput(mealId, "image", note, path, today()))
        _closeScreen.value = true
    }

    fun cancelImagePreview() {
        _errorMessage.value = null
        _imageNote.value = ""
        _pendingImagePath.value = null
        _step.value = AddMealStep.InputSelection
    }

    fun switchToManualFallback() { _errorMessage.value = null; _step.value = AddMealStep.ManualFallback }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
        _imageNote.value = ""
        _pendingImagePath.value = null
    }

    fun saveManualMeal() {
        viewModelScope.launch {
            val cal = _manualCal.value.toIntOrNull() ?: 0
            if (cal <= 0) { _errorMessage.value = "הקלוריות חייבות להיות גדולות מ-0"; return@launch }
            val description = _mealDescription.value.ifEmpty { "ארוחה ידנית" }
            val protein = _manualProtein.value.toIntOrNull() ?: 0
            val carbs = _manualCarbs.value.toIntOrNull() ?: 0
            val fat = _manualFat.value.toIntOrNull() ?: 0
            val items = listOf(MealItem(description, "1 מנה", cal, protein, carbs, fat))
            mealRepository.addMeal(today(), "text", description, items, MealTotals(cal, protein, carbs, fat), null, null)
            _closeScreen.value = true
        }
    }

    fun reset() {
        _step.value = AddMealStep.InputSelection
        _mealDescription.value = ""
        _imageNote.value = ""
        _pendingImagePath.value = null
        _errorMessage.value = null
        _manualCal.value = ""; _manualProtein.value = ""; _manualCarbs.value = ""; _manualFat.value = ""
        _closeScreen.value = false
    }

    fun consumeClose() { _closeScreen.value = false }
}
