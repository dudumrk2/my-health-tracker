package com.myhealthtracker.app.ui.meal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.R
import kotlin.math.roundToInt

@Composable
fun MealReminderOverlay(
    isVisible: Boolean,
    onLogMeal: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit
) {
    // We use a Box filling the max size to act as an overlay (can add dim background if needed)
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }, // Slide from bottom
            animationSpec = spring(
                dampingRatio = 0.6f, // Slight bounce (overshoot)
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            
            // The Infinite Transition for the Idle Loop (Hover + Breathing)
            val infiniteTransition = rememberInfiniteTransition(label = "idle_loop")
            
            // Hover: Up and down movement
            val hoverOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -15f, // pixels to float up
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "hover"
            )
            
            // Breathing: Slight scaling
            val breathingScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathing"
            )

            // Main Popup Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // The Card Background (Native UI)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp), // Space for the character to pop out of the top
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "היי, לא שכחת משהו?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "הגיע הזמן לעדכן את הארוחה האחרונה שלך! המלצר שלנו כבר מחכה.",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onLogMeal,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("רשום ארוחה", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = onRemindLater,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("תזכיר לי עוד 30 דק'")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(onClick = onDismiss) {
                            Text("ביטול", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // The Waiter Character Image (Animated)
                // Placed outside the Card so it overlaps the top edge
                // Ensure you have R.drawable.waiter
                Image(
                    painter = painterResource(id = R.drawable.waiter),
                    contentDescription = "Waiter",
                    modifier = Modifier
                        .size(200.dp)
                        .offset { IntOffset(x = 0, y = hoverOffset.roundToInt()) }
                        .graphicsLayer {
                            scaleX = breathingScale
                            scaleY = breathingScale
                        }
                )
            }
        }
    }
}
