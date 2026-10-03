package com.vx.anymaker.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Resize settings the user last confirmed. A null dimension means "not set". */
data class ResizeSettings(
    val width: Int? = 1000,
    val height: Int? = 1000,
    val keepAspect: Boolean = true,
    val neverReducePixels: Boolean = false,
    val maxKb: Double = 100.0,
    val webp: Boolean = false,
    val locked: Boolean = false,
)

interface ResizeSettingsSource {
    val settings: Flow<ResizeSettings>
    suspend fun save(settings: ResizeSettings)
}

class DataStoreResizeSettings(private val store: DataStore<Preferences>) : ResizeSettingsSource {

    override val settings: Flow<ResizeSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = ResizeSettings()
            ResizeSettings(
                width = if (p.contains(WIDTH)) p[WIDTH]?.takeIf { it > 0 } else defaults.width,
                height = if (p.contains(HEIGHT)) p[HEIGHT]?.takeIf { it > 0 } else defaults.height,
                keepAspect = p[KEEP_ASPECT] ?: defaults.keepAspect,
                neverReducePixels = p[NEVER_REDUCE] ?: defaults.neverReducePixels,
                maxKb = p[MAX_KB] ?: defaults.maxKb,
                webp = p[WEBP] ?: defaults.webp,
                locked = p[LOCKED] ?: defaults.locked,
            )
        }

    override suspend fun save(settings: ResizeSettings) {
        store.edit { p ->
            // 0 is stored for "not set" so a cleared field stays cleared after restart.
            p[WIDTH] = settings.width ?: 0
            p[HEIGHT] = settings.height ?: 0
            p[KEEP_ASPECT] = settings.keepAspect
            p[NEVER_REDUCE] = settings.neverReducePixels
            p[MAX_KB] = settings.maxKb
            p[WEBP] = settings.webp
            p[LOCKED] = settings.locked
        }
    }

    private companion object {
        val WIDTH = intPreferencesKey("resize_width")
        val HEIGHT = intPreferencesKey("resize_height")
        val KEEP_ASPECT = booleanPreferencesKey("resize_keep_aspect")
        val NEVER_REDUCE = booleanPreferencesKey("resize_never_reduce")
        val MAX_KB = doublePreferencesKey("resize_max_kb")
        val WEBP = booleanPreferencesKey("resize_webp")
        val LOCKED = booleanPreferencesKey("resize_locked")
    }
}
