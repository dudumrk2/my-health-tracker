package com.myhealthtracker.app.ui.meal

import androidx.lifecycle.ViewModel
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the existing-meal result/editor screen (detail "edit", analysis-complete
 * notification, and unseen-meal interception). On load it marks the meal seen so it stops
 * popping; [celebrateQuality] is fired by the screen when surfaced via interception.
 */
class MealEditViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val celebrationController: CelebrationController = AppContainer.celebrationController
) : ViewModel() {

    private var mealId: String? = null
    private var date: String = ""

    private val _description = MutableStateFlow(""); val description: StateFlow<String> = _description.asStateFlow()
    private val _items = MutableStateFlow<List<MealItem>>(emptyList()); val recognizedItems: StateFlow<List<MealItem>> = _items.asStateFlow()
    private val _excluded = MutableStateFlow<Set<Int>>(emptySet()); val excludedIndices: StateFlow<Set<Int>> = _excluded.asStateFlow()
    private val _recommendation = MutableStateFlow<String?>(null); val recommendation: StateFlow<String?> = _recommendation.asStateFlow()
    private val _quality = MutableStateFlow<MealQuality?>(null); val quality: StateFlow<MealQuality?> = _quality.asStateFlow()
    private val _imagePath = MutableStateFlow<String?>(null); val imagePath: StateFlow<String?> = _imagePath.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null); val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _saved = MutableStateFlow(false); val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun load(meal: MealEntry) {
        mealId = meal.mealId; date = meal.date
        _description.value = meal.description
        _items.value = meal.items
        _excluded.value = emptySet()
        _recommendation.value = meal.recommendation
        _quality.value = meal.quality
        _imagePath.value = meal.localImagePath
        _saved.value = false
        if (!meal.seen) mealRepository.markMealSeen(meal.mealId)
    }

    fun updateItem(index: Int, item: MealItem) {
        val list = _items.value.toMutableList()
        if (index in list.indices) { list[index] = item; _items.value = list }
    }

    fun toggleItemRemoved(index: Int) {
        if (index !in _items.value.indices) return
        val cur = _excluded.value
        _excluded.value = if (index in cur) cur - index else cur + index
    }

    fun addItem(item: MealItem): Int {
        val list = _items.value.toMutableList(); list.add(item); _items.value = list
        return list.size - 1
    }

    fun save() {
        val id = mealId ?: return
        val active = _items.value.filterIndexed { i, _ -> i !in _excluded.value }
        if (active.isEmpty()) { _errorMessage.value = "כל הפריטים הוסרו"; return }
        mealRepository.updateMeal(id, _description.value, active, MealTotals.fromItems(active))
        _saved.value = true
    }

    fun celebrateQuality() {
        celebrationController.celebrateNow(CelebrationRules.mealQuality(_quality.value, date))
    }
}
