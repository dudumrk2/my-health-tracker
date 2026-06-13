package com.myhealthtracker.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.health.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddWorkoutViewModel(
    private val healthRepository: HealthRepository? = try { HealthRepository() } catch (e: Exception) { null },
    private val getUid: () -> String? = { try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null } }
) : ViewModel() {

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _durationStr = MutableStateFlow("")
    val durationStr: StateFlow<String> = _durationStr.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

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

        viewModelScope.launch {
            try {
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val startTimestamp = Timestamp.now()

                // Update in-memory state for immediate UI feedback (source = "manual" set inside addWorkout)
                FakeRepository.addWorkout(
                    date = todayStr,
                    type = type,
                    durationMin = duration,
                    startTime = startTimestamp
                )

                // Persist to Firestore when user is authenticated
                val uid = getUid()
                if (uid != null) {
                    healthRepository?.saveManualWorkout(uid, todayStr, type, duration, startTimestamp)?.collect()
                }

                _isSaved.value = true
            } catch (e: Exception) {
                _errorMessage.value = "שגיאה בשמירת האימון: ${e.message}"
            }
        }
    }
}
