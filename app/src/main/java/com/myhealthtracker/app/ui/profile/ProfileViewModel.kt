package com.myhealthtracker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.data.profile.genderToHebrew
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Saved : ProfileUiState()
    data class Loaded(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository = AppContainer.profileRepository,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _calculatedAge = MutableStateFlow(0)
    val calculatedAge: StateFlow<Int> = _calculatedAge.asStateFlow()

    init {
        loadProfile()
    }

    fun resetState() {
        _uiState.value = ProfileUiState.Idle
        loadProfile()
    }

    fun loadProfile() {
        val uid = uidProvider() ?: run {
            _uiState.value = ProfileUiState.Idle
            return
        }
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            profileRepository.getUserProfile(uid).collect { result ->
                val profile = result.getOrNull()
                if (profile != null) {
                    val hebrewGender = genderToHebrew(profile.gender)
                    _uiState.value = ProfileUiState.Loaded(profile.copy(gender = hebrewGender))
                    updateAge(profile.birthYear)
                } else {
                    _uiState.value = ProfileUiState.Idle
                }
            }
        }
    }

    fun updateAge(birthYear: Int) {
        if (birthYear <= 0) {
            _calculatedAge.value = 0
            return
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val age = currentYear - birthYear
        _calculatedAge.value = if (age >= 0) age else 0
    }

    fun saveProfile(birthYearStr: String, weightStr: String, heightStr: String, gender: String, themePreference: String = "system") {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            val birthYear = birthYearStr.toIntOrNull() ?: 0
            val weight = weightStr.toDoubleOrNull() ?: 0.0
            val height = heightStr.toDoubleOrNull() ?: 0.0
            
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (birthYear < 1900 || birthYear > currentYear) {
                _uiState.value = ProfileUiState.Error("שנת הלידה חייבת להיות בין 1900 ל-$currentYear")
                return@launch
            }
            if (gender.isEmpty()) {
                _uiState.value = ProfileUiState.Error("אנא בחר מין")
                return@launch
            }
            if (weight < 30.0 || weight > 300.0) {
                _uiState.value = ProfileUiState.Error("המשקל חייב להיות בין 30.0 ל-300.0 ק״ג")
                return@launch
            }
            if (height < 100.0 || height > 250.0) {
                _uiState.value = ProfileUiState.Error("הגובה חייב להיות בין 100.0 ל-250.0 ס״מ")
                return@launch
            }

            val englishGender = when (gender) {
                "זכר" -> "male"
                "נקבה" -> "female"
                "אחר" -> "other"
                else -> gender
            }

            val profile = UserProfile(
                birthYear = birthYear,
                weightKg = weight,
                heightCm = height,
                gender = englishGender,
                themePreference = themePreference
            )

            val uid = uidProvider() ?: run {
                _uiState.value = ProfileUiState.Error("נדרשת התחברות מחדש.")
                return@launch
            }

            profileRepository.saveUserProfile(uid, profile).collect { result ->
                if (result.isSuccess) {
                    _uiState.value = ProfileUiState.Saved
                } else {
                    _uiState.value = ProfileUiState.Error(result.exceptionOrNull()?.message ?: "שגיאה בשמירה")
                }
            }
        }
    }
}
