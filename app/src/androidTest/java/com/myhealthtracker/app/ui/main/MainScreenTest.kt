package com.myhealthtracker.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.myhealthtracker.app.ui.auth.AuthScreen
import com.myhealthtracker.app.ui.auth.AuthViewModel
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

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
