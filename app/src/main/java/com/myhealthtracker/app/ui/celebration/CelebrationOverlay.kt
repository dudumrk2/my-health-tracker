package com.myhealthtracker.app.ui.celebration

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationEvent
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.delay

private const val TEXT_ONLY_DURATION_MS = 2200L

/**
 * Full-screen celebration layer. Hosted once at the app root so it overlays every
 * screen. Collects [controller] events and shows one at a time: a Lottie animation
 * (resolved from res/raw by name), a Hebrew message, a gentle sound (only off
 * silent mode), and a light haptic. Degrades to text-only when an asset is missing.
 */
@Composable
fun CelebrationOverlay(
    controller: CelebrationController = AppContainer.celebrationController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<CelebrationEvent?>(null) }

    LaunchedEffect(controller) {
        controller.events.collect { current = it }
    }

    val event = current ?: return
    val visuals = remember(event) { CelebrationVisuals.forType(event.type) }
    val animationName = remember(event) { visuals.animations.random() }
    val animationResId = remember(animationName) {
        context.resources.getIdentifier(animationName, "raw", context.packageName)
    }

    LaunchedEffect(event) {
        playApplause(context)
        vibrate(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { current = null },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (animationResId != 0) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationResId))
                val progress by animateLottieCompositionAsState(composition, iterations = 1)
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(220.dp)
                )
                LaunchedEffect(progress) {
                    if (composition != null && progress >= 1f) current = null
                }
            } else {
                // No asset bundled yet — show text only and auto-dismiss.
                LaunchedEffect(event) {
                    delay(TEXT_ONLY_DURATION_MS)
                    current = null
                }
            }
            Text(
                text = visuals.message,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
            )
        }
    }
}

/** Plays the applause sound only when the ringer is in normal mode (respects silent/vibrate). */
private fun playApplause(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    val resId = context.resources.getIdentifier("celeb_applause", "raw", context.packageName)
    if (resId == 0) return
    val player = MediaPlayer.create(context, resId) ?: return
    player.setVolume(APPLAUSE_VOLUME, APPLAUSE_VOLUME) // softened so it isn't startling
    player.setOnCompletionListener { it.release() }
    player.start()
}

/** Playback volume for the applause sound (0f–1f). Kept gentle so celebrations aren't jarring. */
private const val APPLAUSE_VOLUME = 0.45f

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    // minSdk 28 → VibrationEffect always available.
    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
}
