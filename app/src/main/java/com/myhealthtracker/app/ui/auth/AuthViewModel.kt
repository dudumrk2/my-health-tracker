package com.myhealthtracker.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.FakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    application: Application
) : AndroidViewModel(application) {

    val isUserLoggedIn: StateFlow<Boolean> = FakeRepository.isUserLoggedIn
    
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun handleGoogleSignInMock() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            kotlinx.coroutines.delay(1000) // Simulate network delay
            FakeRepository.login()
            _uiState.value = AuthUiState.Success
        }
    }

    fun handleSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun signOut() {
        FakeRepository.logout()
        _uiState.value = AuthUiState.Idle
    }
}
