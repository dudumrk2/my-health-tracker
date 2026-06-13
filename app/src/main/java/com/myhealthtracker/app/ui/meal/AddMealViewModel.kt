package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.FakeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class AddMealStep {
    object InputSelection : AddMealStep()
    object Loading : AddMealStep()
    object ResultState : AddMealStep()
    object ManualFallback : AddMealStep()
}

class AddMealViewModel(
    private val mealRepository: MealRepository = FakeRepository
) : ViewModel() {

    private val _step = MutableStateFlow<AddMealStep>(AddMealStep.InputSelection)
    val step: StateFlow<AddMealStep> = _step.asStateFlow()

    private val _mealDescription = MutableStateFlow("")
    val mealDescription: StateFlow<String> = _mealDescription.asStateFlow()

    private val _recognizedItems = MutableStateFlow<List<MealItem>>(emptyList())
    val recognizedItems: StateFlow<List<MealItem>> = _recognizedItems.asStateFlow()

    // Manual input fields
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

    fun onDescriptionChange(desc: String) {
        _mealDescription.value = desc
        _errorMessage.value = null
    }

    fun onManualCalChange(value: String) { _manualCal.value = value }
    fun onManualProteinChange(value: String) { _manualProtein.value = value }
    fun onManualCarbsChange(value: String) { _manualCarbs.value = value }
    fun onManualFatChange(value: String) { _manualFat.value = value }

    fun analyzeMeal(isImage: Boolean = false) {
        if (!isImage && _mealDescription.value.trim().isEmpty()) {
            _errorMessage.value = "אנא הקלד תיאור של הארוחה"
            return
        }

        viewModelScope.launch {
            _step.value = AddMealStep.Loading
            delay(1500) // Simulate AI analysis latency

            val desc = _mealDescription.value.trim()
            if (desc == "שגיאה" || desc == "error") {
                // Trigger simulated error state
                _errorMessage.value = "אירעה שגיאה בניתוח ה-AI. אנא נסה שנית או הזן ידנית."
                _step.value = AddMealStep.InputSelection
                return@launch
            }

            // Generate mock recognized items
            val mockItems = if (desc.contains("סלט")) {
                listOf(
                    MealItem("חזה עוף בגריל", "150 גרם", 250, 46, 0, 5),
                    MealItem("ירקות מעורבים לסלט", "1 קערה", 60, 2, 8, 0),
                    MealItem("שמן זית (רוטב)", "1 כף", 120, 0, 0, 14),
                    MealItem("אבוקדו", "חצי יחידה", 160, 2, 8, 15)
                )
            } else if (desc.contains("בורגר") || desc.contains("המבורגר")) {
                listOf(
                    MealItem("קציצת בקר", "180 גרם", 350, 28, 0, 25),
                    MealItem("לחמניית המבורגר", "1 יחידה", 220, 6, 40, 3),
                    MealItem("רטבים וירקות", "1 מנה", 80, 1, 10, 4)
                )
            } else {
                // Default fallback chicken rice
                listOf(
                    MealItem("חזה עוף מבושל", "150 גרם", 240, 44, 0, 4),
                    MealItem("אורז מבושל", "1 כוס", 200, 4, 45, 0),
                    MealItem("ברוקולי מאודה", "1 מנה", 40, 3, 7, 0)
                )
            }

            _recognizedItems.value = mockItems
            _step.value = AddMealStep.ResultState
        }
    }

    fun updateItem(index: Int, updatedItem: MealItem) {
        val currentList = _recognizedItems.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = updatedItem
            _recognizedItems.value = currentList
        }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            val finalDescription = if (_step.value == AddMealStep.ManualFallback) {
                _mealDescription.value.ifEmpty { "ארוחה ידנית" }
            } else {
                _mealDescription.value.ifEmpty { "ארוחה מנותחת AI" }
            }

            val finalItems: List<MealItem>
            val totals: MealTotals

            if (_step.value == AddMealStep.ManualFallback) {
                val cal = _manualCal.value.toIntOrNull() ?: 0
                val protein = _manualProtein.value.toIntOrNull() ?: 0
                val carbs = _manualCarbs.value.toIntOrNull() ?: 0
                val fat = _manualFat.value.toIntOrNull() ?: 0

                if (cal <= 0) {
                    _errorMessage.value = "הקלוריות חייבות להיות גדולות מ-0"
                    return@launch
                }

                finalItems = listOf(MealItem(finalDescription, "1 מנה", cal, protein, carbs, fat))
                totals = MealTotals(cal, protein, carbs, fat)
            } else {
                if (_recognizedItems.value.isEmpty()) {
                    _errorMessage.value = "אין פריטים לשמירה"
                    return@launch
                }
                finalItems = _recognizedItems.value
                totals = MealTotals(
                    calories = finalItems.sumOf { it.calories },
                    proteinG = finalItems.sumOf { it.proteinG },
                    carbsG = finalItems.sumOf { it.carbsG },
                    fatG = finalItems.sumOf { it.fatG }
                )
            }

            mealRepository.addMeal(
                date = todayStr,
                inputType = if (_step.value == AddMealStep.ManualFallback) "text" else "image",
                description = finalDescription,
                items = finalItems,
                totals = totals
            )
            _isSaved.value = true
        }
    }

    fun switchToManualFallback() {
        _errorMessage.value = null
        _step.value = AddMealStep.ManualFallback
    }

    fun resetToInput() {
        _errorMessage.value = null
        _step.value = AddMealStep.InputSelection
    }
}
