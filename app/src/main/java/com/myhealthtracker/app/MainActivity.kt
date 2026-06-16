package com.myhealthtracker.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.QuickActionsNotificationManager
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentState.value = intent

        enableEdgeToEdge()
        setContent {
            val authUser by AppContainer.authManager.authState.collectAsState()
            val profileData by remember(authUser) {
                if (authUser != null) {
                    AppContainer.profileRepository.getUserProfile(authUser!!.uid)
                } else {
                    flowOf(Result.success(null))
                }
            }.collectAsState(initial = Result.success(null))

            val themePreference = profileData.getOrNull()?.themePreference ?: "system"
            val quickActionsEnabled = profileData.getOrNull()?.quickActionsEnabled ?: true

            val darkTheme = when (themePreference) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            val context = LocalContext.current
            LaunchedEffect(authUser, quickActionsEnabled) {
                if (authUser != null && quickActionsEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            QuickActionsNotificationManager.start(context)
                        } else {
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                1001
                            )
                        }
                    } else {
                        QuickActionsNotificationManager.start(context)
                    }
                } else {
                    QuickActionsNotificationManager.stop(context)
                }
            }

            MyHealthTrackerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val currentIntent by intentState
                    MainNavigation(
                        intent = currentIntent,
                        onIntentHandled = { intentState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentState.value = intent
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            QuickActionsNotificationManager.start(this)
        }
    }
}
