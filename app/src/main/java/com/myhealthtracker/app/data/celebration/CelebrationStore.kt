package com.myhealthtracker.app.data.celebration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Records which celebrations have already been shown so each fires only once. */
interface CelebrationStore {
    suspend fun hasCelebrated(key: String): Boolean
    suspend fun markCelebrated(key: String)
}

/** Test/fallback implementation with no persistence. */
class InMemoryCelebrationStore : CelebrationStore {
    private val keys = mutableSetOf<String>()
    override suspend fun hasCelebrated(key: String): Boolean = keys.contains(key)
    override suspend fun markCelebrated(key: String) { keys.add(key) }
}

private val Context.celebrationDataStore by preferencesDataStore(name = "celebrations")

/** Local, per-device persistence. Never synced to Firestore (display concern only). */
class DataStoreCelebrationStore(context: Context) : CelebrationStore {
    private val appContext = context.applicationContext
    private val keysPref = stringSetPreferencesKey("celebrated_keys")

    override suspend fun hasCelebrated(key: String): Boolean {
        val prefs = appContext.celebrationDataStore.data.first()
        return prefs[keysPref]?.contains(key) == true
    }

    override suspend fun markCelebrated(key: String) {
        appContext.celebrationDataStore.edit { prefs ->
            prefs[keysPref] = (prefs[keysPref] ?: emptySet()) + key
        }
    }
}
