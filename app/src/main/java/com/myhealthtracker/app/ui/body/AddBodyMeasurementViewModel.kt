package com.myhealthtracker.app.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.data.FakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddBodyMeasurementViewModel(
    private val bodyMeasurementRepository: BodyMeasurementRepository = FakeRepository
) : ViewModel() {

    private val _weightStr = MutableStateFlow("")
    val weightStr: StateFlow<String> = _weightStr.asStateFlow()

    private val _waistStr = MutableStateFlow("")
    val waistStr: StateFlow<String> = _waistStr.asStateFlow()

    private val _hipsStr = MutableStateFlow("")
    val hipsStr: StateFlow<String> = _hipsStr.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    init {
        loadLastValues()
    }

    private fun loadLastValues() {
        // Prefill with last recorded values ordered by date (not insertion order)
        val last = bodyMeasurementRepository.bodyMeasurements.value.maxByOrNull { it.date }
        if (last != null) {
            _weightStr.value = last.weightKg?.toString() ?: ""
            _waistStr.value = last.waistCm?.toString() ?: ""
            _hipsStr.value = last.hipsCm?.toString() ?: ""
        }
    }

    fun onWeightChange(value: String) {
        _weightStr.value = value
        _errorMessage.value = null
    }

    fun onWaistChange(value: String) {
        _waistStr.value = value
        _errorMessage.value = null
    }

    fun onHipsChange(value: String) {
        _hipsStr.value = value
        _errorMessage.value = null
    }

    fun onNoteChange(value: String) {
        _note.value = value
        _errorMessage.value = null
    }

    fun saveMeasurement() {
        val weight = _weightStr.value.toDoubleOrNull()
        val waist = _waistStr.value.toDoubleOrNull()
        val hips = _hipsStr.value.toDoubleOrNull()

        if (weight != null && (weight <= 0.0 || weight > 500.0)) {
            _errorMessage.value = "המשקל חייב להיות בין 0 ל-500 ק״ג"
            return
        }
        if (waist != null && (waist <= 0.0 || waist > 300.0)) {
            _errorMessage.value = "היקף המותן חייב להיות בין 0 ל-300 ס״מ"
            return
        }
        if (hips != null && (hips <= 0.0 || hips > 300.0)) {
            _errorMessage.value = "היקף הירכיים חייב להיות בין 0 ל-300 ס״מ"
            return
        }

        viewModelScope.launch {
            try {
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                bodyMeasurementRepository.addBodyMeasurement(
                    date = todayStr,
                    weight = weight,
                    waist = waist,
                    hips = hips,
                    note = _note.value
                )
                _isSaved.value = true
            } catch (e: Exception) {
                _errorMessage.value = "שגיאה בשמירת המדדים: ${e.message}"
            }
        }
    }
}
