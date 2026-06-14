package com.myhealthtracker.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.myhealthtracker.app.R
import com.myhealthtracker.app.theme.MyHealthTrackerTheme

private val ShieldShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h * 0.45f)
    quadraticTo(w, h * 0.75f, w * 0.5f, h)
    quadraticTo(0f, h * 0.75f, 0f, h * 0.45f)
    close()
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Navigation is driven by the authenticated user, not by a single sign-in event,
    // so an already-signed-in user is routed straight past this screen.
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onAuthSuccess()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    viewModel.handleGoogleSignIn(idToken)
                } else {
                    viewModel.handleSignInError("לא התקבל אסימון התחברות. נסה שוב.")
                }
            } catch (e: ApiException) {
                viewModel.handleSignInError("ההתחברות נכשלה (קוד ${e.statusCode}).")
            }
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onGoogleSignInClick = {
            val clientIdResId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            if (clientIdResId == 0) {
                Log.e("AuthScreen", "default_web_client_id missing — google-services.json not configured")
                viewModel.handleSignInError("האפליקציה אינה מוגדרת להתחברות עם Google.")
            } else {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(clientIdResId))
                    .requestEmail()
                    .build()
                launcher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
            }
        },
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
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
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
            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Title Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shield Logo Icon (Stitch Spec)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimary,
                                shape = ShieldShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.5.dp, height = 18.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(1.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 4.5.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "MyHealthTracker",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "המלווה החכם לבריאות שלך",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Banner Image (Stitch Spec)
            Image(
                painter = painterResource(id = R.drawable.login_banner),
                contentDescription = "סלסלת ירקות ופירות טריים לתזונה בריאה",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

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
                    // Premium White Google Sign-in Button with border and G logo
                    OutlinedButton(
                        onClick = onGoogleSignInClick,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
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
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "לוגו גוגל צבעוני",
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp)
                            )
                            Text(
                                text = "המשך עם Google",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email Login Link
                    Text(
                        text = "כניסה באמצעות דואר אלקטרוני",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clickable { onGoogleSignInClick() } // Mock action
                            .padding(8.dp)
                    )
                }

                if (uiState is AuthUiState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Footer Privacy Terms Info (Stitch Spec)
            Text(
                text = "בלחיצה על \"המשך\", הנך מסכים/ה לתנאי השימוש\nולמדיניות הפרטיות שלנו.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
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
