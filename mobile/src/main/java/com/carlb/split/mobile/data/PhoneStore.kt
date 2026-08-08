package com.carlb.split.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carlb.split.core.ShotString
import com.carlb.split.core.Wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore by preferencesDataStore("split_phone")

/**
 * The phone's replica of the watch log. Upsert-by-id, because DataClient may
 * redeliver the same item — replaying a session must not duplicate it.
 */
class PhoneStore(context: Context) {

    private val ds = context.applicationContext.dataStore
    private val key = stringPreferencesKey("strings")

    fun strings(): Flow<List<ShotString>> = ds.data.map { prefs ->
        decode(prefs[key]).sortedByDescending { it.epochMillis }
    }

    suspend fun upsert(s: ShotString) {
        ds.edit { prefs ->
            val current = decode(prefs[key]).filterNot { it.id == s.id }
            prefs[key] = Wire.json.encodeToString((listOf(s) + current).take(MAX))
        }
    }

    suspend fun setHitFactor(id: String, hf: Double, powerFactor: String) {
        ds.edit { prefs ->
            val next = decode(prefs[key]).map {
                if (it.id == id) it.copy(hitFactor = hf, powerFactor = powerFactor) else it
            }
            prefs[key] = Wire.json.encodeToString(next)
        }
    }

    suspend fun clear() {
        ds.edit { it[key] = "[]" }
    }

    private fun decode(raw: String?): List<ShotString> =
        raw?.let {
            runCatching { Wire.json.decodeFromString<List<ShotString>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    private companion object { const val MAX = 2000 }
}
