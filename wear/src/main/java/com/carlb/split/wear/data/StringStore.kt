package com.carlb.split.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carlb.split.core.ShotString
import com.carlb.split.core.TimerConfig
import com.carlb.split.core.Wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore by preferencesDataStore("split_wear")

/**
 * On-watch storage. Deliberately the source of truth: strings land here before
 * any attempt to reach the phone, so a session shot entirely out of Bluetooth
 * range is still complete when you get home.
 */
class StringStore(context: Context) {

    private val ds = context.applicationContext.dataStore

    private val keyStrings = stringPreferencesKey("strings")
    private val keyConfig = stringPreferencesKey("config")

    fun strings(): Flow<List<ShotString>> = ds.data.map { prefs ->
        prefs[keyStrings]?.let { raw ->
            runCatching { Wire.json.decodeFromString<List<ShotString>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    fun config(): Flow<TimerConfig> = ds.data.map { prefs ->
        prefs[keyConfig]?.let { raw ->
            runCatching { Wire.decodeConfig(raw) }.getOrDefault(TimerConfig())
        } ?: TimerConfig()
    }

    suspend fun append(s: ShotString) {
        ds.edit { prefs ->
            val current = prefs[keyStrings]?.let {
                runCatching { Wire.json.decodeFromString<List<ShotString>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = (listOf(s) + current).take(MAX_RETAINED)
            prefs[keyStrings] = Wire.json.encodeToString(next)
        }
    }

    suspend fun saveConfig(cfg: TimerConfig) {
        ds.edit { it[keyConfig] = Wire.encodeConfig(cfg) }
    }

    suspend fun clear() {
        ds.edit { it[keyStrings] = "[]" }
    }

    private companion object { const val MAX_RETAINED = 400 }
}
