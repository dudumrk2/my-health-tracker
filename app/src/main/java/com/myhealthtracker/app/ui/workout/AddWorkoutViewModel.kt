package com.myhealthtracker.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.FirestoreHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddWorkoutViewModel(
    private val healthRepository: HealthRepository = FakeRepository,
    private val firestoreHealthRepository: HealthRepository? = try { FirestoreHealthRepository() } catch (e: Exception) { null },
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
                val startInstant = Instant.now()

                // Intentional hybrid write pattern: update the in-memory fake repository for immediate mock UI reactivity,
                // and also persist to Firestore in the background if the user is authenticated.
                healthRepository.saveManualWorkout(
                    uid = "mock_uid",
                    date = todayStr,
                    type = type,
                    durationMin = duration,
                    startTime = startInstant
                ).collect()

                // Persist to Firestore when user is authenticated
                val uid = getUid()
                if (uid != null) {
                    firestoreHealthRepository?.saveManualWorkout(uid, todayStr, type, duration, startInstant)?.collect()
                }

                _isSaved.value = true
            } catch (e: Exception) {
                _errorMessage.value = "שגיאה בשמירת האימון: ${e.message}"
            }
        }
    }
}
