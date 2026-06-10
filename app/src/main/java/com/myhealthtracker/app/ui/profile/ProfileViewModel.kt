package com.myhealthtracker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Saved : ProfileUiState()
    data class Loaded(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val authManager: AuthManager = AuthManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _calculatedAge = MutableStateFlow(0)
    val calculatedAge: StateFlow<Int> = _calculatedAge.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val uid = authManager.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            profileRepository.getUserProfile(uid).collect { result ->
                if (result.isSuccess) {
                    val profile = result.getOrNull()
                    if (profile != null) {
                        _uiState.value = ProfileUiState.Loaded(profile)
                        updateAge(profile.birthYear)
                    } else {
                        _uiState.value = ProfileUiState.Idle
                    }
                } else {
                    _uiState.value = ProfileUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load profile")
                }
            }
        }
    }

    fun updateAge(birthYear: Int) {
        _calculatedAge.value = profileRepository.calculateAge(birthYear)
    }

    fun saveProfile(birthYearStr: String, weightStr: String, heightStr: String) {
        val uid = authManager.currentUser?.uid ?: return

        val birthYear = birthYearStr.toIntOrNull() ?: 0
        val weight = weightStr.toDoubleOrNull() ?: 0.0
        val height = heightStr.toDoubleOrNull() ?: 0.0

        val profile = UserProfile(
            birthYear = birthYear,
            weightKg = weight,
            heightCm = height
        )

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val validation = profileRepository.validateProfile(profile)
            if (validation.isFailure) {
                _uiState.value = ProfileUiState.Error(validation.exceptionOrNull()?.message ?: "Validation failed")
                return@launch
            }

            val result = profileRepository.saveUserProfile(uid, profile).first()
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Saved
            } else {
                _uiState.value = ProfileUiState.Error(result.exceptionOrNull()?.message ?: "Failed to save profile")
            }
        }
    }
}
