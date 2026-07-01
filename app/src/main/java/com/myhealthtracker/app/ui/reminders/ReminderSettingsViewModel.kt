package com.myhealthtracker.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.reminders.ReminderSettings
import com.myhealthtracker.app.data.reminders.ReminderSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Edits reminder settings. Each change persists to the store and calls [onChanged] so the
 * caller can re-arm the AlarmManager schedule (kept out of the ViewModel for unit-testability).
 */
class ReminderSettingsViewModel(
    private val store: ReminderSettingsStore,
    private val onChanged: (ReminderSettings) -> Unit
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings.DEFAULT)

    fun setMasterEnabled(enabled: Boolean) =
        persist(settings.value.copy(masterEnabled = enabled))

    fun setSoundEnabled(enabled: Boolean) =
        persist(settings.value.copy(soundEnabled = enabled))

    fun setSlotEnabled(index: Int, enabled: Boolean) = persist(
        settings.value.copy(slots = settings.value.slots.mapIndexed { i, s ->
            if (i == index) s.copy(enabled = enabled) else s
        })
    )

    fun setSlotTime(index: Int, time: LocalTime) = persist(
        settings.value.copy(slots = settings.value.slots.mapIndexed { i, s ->
            if (i == index) s.copy(time = time) else s
        })
    )

    private fun persist(new: ReminderSettings) {
        viewModelScope.launch {
            store.update(new)
            onChanged(new)
        }
    }
}
