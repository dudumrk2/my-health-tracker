package com.myhealthtracker.app.ui.reminders

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.notification.ReminderScheduler
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: ReminderSettingsViewModel = viewModel {
        ReminderSettingsViewModel(AppContainer.reminderSettingsStore) { settings ->
            ReminderScheduler.armAll(context, settings)
        }
    }
    val settings by vm.settings.collectAsState()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")

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
        Button(onClick = onGrantOverlay, modifier = Modifier.padding(top = 8.dp)) {
            Text("אפשר הצגה מעל אפליקציות אחרות")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("חזרה")
        }
    }
}
