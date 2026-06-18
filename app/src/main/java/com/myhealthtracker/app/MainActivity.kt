package com.myhealthtracker.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.QuickActionsNotificationManager
import com.myhealthtracker.app.theme.MyHealthTrackerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
            // Posts the system permission dialog (Android 13+) and reacts to the result:
            // start the notification on grant, stop it on denial.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    QuickActionsNotificationManager.start(context)
                } else {
                    QuickActionsNotificationManager.stop(context)
                }
            }

            LaunchedEffect(authUser, quickActionsEnabled) {
                if (authUser != null && quickActionsEnabled) {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED

                    if (needsPermission) {
                        // First run on Android 13+: ask for the notification permission so the
                        // ongoing quick-actions notification can actually be posted. (After two
                        // denials the system returns the result without showing UI.)
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
                        onIntentHandled = {
                            intentState.value = null
                            // Replace the launching intent so a configuration-change
                            // recreation (e.g. rotation) doesn't re-deliver the deep link
                            // and navigate a second time.
                            setIntent(Intent())
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Record an app-foreground heartbeat so the server-side inactivity cleanup can
        // distinguish a live install from an abandoned one. No-op when signed out.
        val uid = AppContainer.currentUid() ?: return
        lifecycleScope.launch {
            runCatching { AppContainer.activityRepository.touchLastActive(uid) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }
}
