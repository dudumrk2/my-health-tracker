package com.myhealthtracker.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.theme.MyHealthTrackerTheme

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn) {
            onAuthSuccess()
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onGoogleSignInClick = { viewModel.handleGoogleSignInMock() },
        modifier = modifier
    )
}

@Composable
private fun AuthScreenContent(
    uiState: AuthUiState,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.background
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Spacing
            Spacer(modifier = Modifier.height(32.dp))

            // Logo & Title Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circle Logo Icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💚",
                        fontSize = 48.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "MyHealthTracker",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "המלווה החכם לבריאות שלך",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Action Button Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Button(
                        onClick = onGoogleSignInClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🌐", // Mock Google Icon
                                modifier = Modifier.padding(end = 8.dp),
                                fontSize = 20.sp
                            )
                            Text(
                                text = "המשך עם Google",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                if (uiState is AuthUiState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Footer Privacy Terms Info
            Text(
                text = "בהתחברותך, אתה מסכים לתנאי השימוש ומדיניות הפרטיות.\nהאפליקציה אינה מספקת ייעוץ רפואי.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
                lineHeight = 16.sp
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun AuthScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        AuthScreenContent(
            uiState = AuthUiState.Idle,
            onGoogleSignInClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun AuthScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        AuthScreenContent(
            uiState = AuthUiState.Idle,
            onGoogleSignInClick = {}
        )
    }
}
