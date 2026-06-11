package com.myhealthtracker.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.myhealthtracker.app.data.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: FirebaseUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val authManager: AuthManager = AuthManager()) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authManager.authState

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authManager.signInWithGoogle(idToken).collect { result ->
                _uiState.value = if (result.isSuccess) {
                    AuthUiState.Success(result.getOrThrow())
                } else {
                    AuthUiState.Error(result.exceptionOrNull()?.message ?: "Google Sign-in failed")
                }
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _uiState.value = AuthUiState.Idle
    }
}
