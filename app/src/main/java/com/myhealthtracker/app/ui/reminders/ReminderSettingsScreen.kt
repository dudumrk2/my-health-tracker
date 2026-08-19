package com.myhealthtracker.app.ui.reminders

import android.app.TimePickerDialog
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.ReminderActivity
import com.myhealthtracker.app.notification.ReminderScheduler
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: ReminderSettingsViewModel = viewModel {
        ReminderSettingsViewModel(AppContainer.reminderSettingsStore) { settings ->
            ReminderScheduler.armAll(context, settings)
        }
    }
    val settings by vm.settings.collectAsState()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    var hasOverlayPermission by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = AndroidSettings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "תזכורות ארוחה",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "תזכורת קופצת בזמן הארוחה כדי שתזכור לצלם ולתעד אותה",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("הפעל תזכורות", fontWeight = FontWeight.Bold)
            Switch(checked = settings.masterEnabled, onCheckedChange = { vm.setMasterEnabled(it) })
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("צליל התראה", fontWeight = FontWeight.Bold)
            Switch(checked = settings.soundEnabled, onCheckedChange = { vm.setSoundEnabled(it) })
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        settings.slots.forEachIndexed { index, slot ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(slot.mealLabel, modifier = Modifier.weight(1f))
                Text(
                    text = slot.time.format(fmt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, m -> vm.setSlotTime(index, java.time.LocalTime.of(h, m)) },
                                slot.time.hour, slot.time.minute, true
                            ).show()
                        }
                )
                Switch(checked = slot.enabled, onCheckedChange = { vm.setSlotEnabled(index, it) })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        if (!hasOverlayPermission) {
            Button(
                onClick = onGrantOverlay,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("אפשר הצגה מעל אפליקציות אחרות")
            }
        }

        OutlinedButton(
            onClick = {
                ReminderActivity.start(context, "ארוחת ניסיון", -1, settings.soundEnabled)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = hasOverlayPermission
        ) {
            Text("בדיקת תזכורת (ניסיון)")
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("חזרה")
        }
    }
}

