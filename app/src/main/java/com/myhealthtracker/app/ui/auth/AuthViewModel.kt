package com.myhealthtracker.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseUser
import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.sync.HealthSyncScheduler
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

class AuthViewModel(
    private val authManager: AuthManager = AppContainer.authManager
) : ViewModel() {

    /** Drives navigation: non-null once Firebase has an authenticated user. */
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
                    AuthUiState.Error(result.exceptionOrNull()?.message ?: "ההתחברות נכשלה. נסה שוב.")
                }
            }
        }
    }

    fun handleSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun signOut(context: Context) {
        authManager.signOut()
        WorkManager.getInstance(context).cancelUniqueWork(HealthSyncScheduler.PERIODIC_WORK_NAME)
        _uiState.value = AuthUiState.Idle
    }
}
