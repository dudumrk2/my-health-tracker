package com.myhealthtracker.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddWorkoutViewModel(
    private val healthRepository: HealthRepository = AppContainer.healthRepository,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : ViewModel() {

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _durationStr = MutableStateFlow("")
    val durationStr: StateFlow<String> = _durationStr.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    /**
     * Restore the initial input state and clear the saved flag. ViewModels here are not
     * scoped per nav entry, so the same instance is reused across openings; without this a
     * stale `isSaved = true` would dismiss the screen the instant it reopens (the "add
     * workout" button appears dead after the first save).
     */
    fun reset() {
        _selectedType.value = null
        _durationStr.value = ""
        _errorMessage.value = null
        _isSaved.value = false
    }

    fun selectType(type: String) {
        _selectedType.value = type
        _errorMessage.value = null
    }

    fun onDurationChange(duration: String) {
        _durationStr.value = duration
        _errorMessage.value = null
    }

    fun saveWorkout() {
        val type = _selectedType.value
        if (type == null) {
            _errorMessage.value = "אנא בחר סוג אימון"
            return
        }

        val duration = _durationStr.value.toIntOrNull() ?: 0
        if (duration <= 0) {
            _errorMessage.value = "משך האימון חייב להיות גדול מ-0 דקות"
            return
        }

        val uid = uidProvider()
        if (uid == null) {
            _errorMessage.value = "נדרשת התחברות מחדש."
            return
        }

        viewModelScope.launch {
            try {
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                healthRepository.saveManualWorkout(
                    uid = uid,
                    date = todayStr,
                    type = type,
                    durationMin = duration,
                    startTime = Instant.now()
                ).collect()
                _isSaved.value = true
            } catch (e: Exception) {
                _errorMessage.value = "שגיאה בשמירת האימון: ${e.message}"
            }
        }
    }
}
