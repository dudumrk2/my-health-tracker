package com.myhealthtracker.app.data.reminders

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

interface ReminderSettingsStore {
    val settings: Flow<ReminderSettings>
    suspend fun update(new: ReminderSettings)
}

/** Test/fallback implementation with no persistence. */
class InMemoryReminderSettingsStore(
    initial: ReminderSettings = ReminderSettings.DEFAULT
) : ReminderSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<ReminderSettings> = state.asStateFlow()
    override suspend fun update(new: ReminderSettings) { state.value = new }
}

private val Context.reminderDataStore by preferencesDataStore(name = "reminders")

/** Local, per-device persistence. Same pattern as DataStoreCelebrationStore. */
class DataStoreReminderSettingsStore(context: Context) : ReminderSettingsStore {
    private val appContext = context.applicationContext
    private val masterKey = booleanPreferencesKey("master_enabled")
    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val slotsKey = stringPreferencesKey("slots")

    override val settings: Flow<ReminderSettings> = appContext.reminderDataStore.data.map { prefs ->
        val slots = prefs[slotsKey]?.let { ReminderSettingsCodec.decodeSlots(it) }
            ?: ReminderSettings.DEFAULT.slots
        ReminderSettings(
            masterEnabled = prefs[masterKey] ?: true,
            soundEnabled = prefs[soundKey] ?: true,
            slots = slots
        )
    }

    override suspend fun update(new: ReminderSettings) {
        appContext.reminderDataStore.edit { prefs ->
            prefs[masterKey] = new.masterEnabled
            prefs[soundKey] = new.soundEnabled
            prefs[slotsKey] = ReminderSettingsCodec.encodeSlots(new.slots)
        }
    }
}
