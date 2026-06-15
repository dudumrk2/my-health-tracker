package com.myhealthtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val authUser by AppContainer.authManager.authState.collectAsState()
      val themePreference by remember(authUser) {
        if (authUser != null) {
          AppContainer.profileRepository.getUserProfile(authUser!!.uid)
            .map { it.getOrNull()?.themePreference ?: "system" }
        } else {
          flowOf("system")
        }
      }.collectAsState(initial = "system")

      val darkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
      }

      MyHealthTrackerTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }
}
