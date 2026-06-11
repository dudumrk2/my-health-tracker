package com.myhealthtracker.app.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        val authViewModel = mockk<AuthViewModel>(relaxed = true)
        composeTestRule.setContent {
            AuthScreen(viewModel = authViewModel, onAuthSuccess = {})
        }
    }

    @Test
    fun appTitle_isDisplayed() {
        composeTestRule.onNodeWithText("My Health Tracker").assertExists()
    }
}
