package com.myhealthtracker.app.ui.main

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.ui.auth.AuthUiState
import com.myhealthtracker.app.ui.auth.AuthViewModel
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_initiallyIdle() = runTest {
        val application  = mockk<Application>(relaxed = true)
        val viewModel    = AuthViewModel(application)
        assertEquals(AuthUiState.Idle, viewModel.uiState.first())
    }
}
