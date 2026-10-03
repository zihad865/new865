package com.vx.anymaker.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** One preferences file for the whole app process. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
