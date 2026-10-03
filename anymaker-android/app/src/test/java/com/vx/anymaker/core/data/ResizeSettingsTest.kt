package com.vx.anymaker.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResizeSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun defaultsThenRoundTrip() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "s.preferences_pb") }
        val source = DataStoreResizeSettings(store)
        assertEquals(ResizeSettings(), source.settings.first())

        val changed = ResizeSettings(width = null, height = 450, keepAspect = false, neverReducePixels = true, maxKb = 75.5, webp = true, locked = true)
        source.save(changed)
        assertEquals(changed, source.settings.first())
    }
}
