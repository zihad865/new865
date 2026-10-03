package com.vx.anymaker.feature.resize

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.R
import com.vx.anymaker.core.data.ResizeSettings
import com.vx.anymaker.core.data.ResizeSettingsSource
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.image.ImageOps
import com.vx.anymaker.core.image.ImportedImage
import com.vx.anymaker.core.image.ShrinkOutput
import com.vx.anymaker.core.image.Shrinker
import com.vx.anymaker.ui.text.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private class FakeSettings(initial: ResizeSettings = ResizeSettings()) : ResizeSettingsSource {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<ResizeSettings> = flow
    override suspend fun save(settings: ResizeSettings) {
        flow.value = settings
    }
}

private class FakeImages : ImageOps {
    val imported = mutableListOf<Uri>()
    val shrinkCalls = mutableListOf<Shrinker.Options>()
    var shrinkGate: CompletableDeferred<Unit>? = null
    var shrinkError: Exception? = null
    var importError: Exception? = null
    var saved = 0

    override suspend fun importToCache(uri: Uri): ImportedImage {
        importError?.let { throw it }
        imported += uri
        return ImportedImage(File("/tmp/${uri.lastPathSegment}"), uri.lastPathSegment ?: "photo", 1000, 4000, 3000)
    }

    override suspend fun shrink(source: ImportedImage, options: Shrinker.Options, onProgress: (String) -> Unit): ShrinkOutput {
        shrinkCalls += options
        onProgress("Compressing…")
        shrinkGate?.await()
        shrinkError?.let { throw it }
        return ShrinkOutput(File("/tmp/out_${source.stem}.jpg"), 1000, 750, 1000, 750, 50_000, 88, false)
    }

    override suspend fun saveToGallery(output: ShrinkOutput) {
        saved++
    }

    override suspend fun writeTo(target: Uri, output: ShrinkOutput) {
        saved++
    }

    override fun shareUri(output: ShrinkOutput): Uri = Uri.parse("content://test/${output.file.name}")
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ResizeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var images: FakeImages
    private lateinit var settings: FakeSettings

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        images = FakeImages()
        settings = FakeSettings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = ResizeViewModel(app, images, settings)
    private fun uri(name: String) = Uri.parse("content://photos/$name")

    @Test
    fun loadsSavedSettingsIntoForm() = runTest(dispatcher) {
        settings.flow.value = ResizeSettings(width = 300, height = null, maxKb = 20.0, webp = true)
        val vm = vm()
        advanceUntilIdle()
        val form = vm.state.value.form
        assertEquals("300", form.width)
        assertEquals("", form.height)
        assertEquals("20", form.maxKb)
        assertTrue(form.webp)
    }

    @Test
    fun pickThenConvert_producesOutputAndSavesSettings() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("a.jpg"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.image)
        assertNull(vm.state.value.output)

        vm.onFormChange(vm.state.value.form.copy(width = "1000", height = "1000", maxKb = "60"))
        vm.convert()
        advanceUntilIdle()
        val s = vm.state.value
        assertNotNull(s.output)
        assertFalse(s.busy)
        assertEquals(60.0, images.shrinkCalls.single().maxKb, 0.0)
        assertEquals(60.0, settings.flow.value.maxKb, 0.0)
    }

    @Test
    fun invalidWidth_showsFieldErrorAndDoesNotConvert() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("a.jpg"))
        advanceUntilIdle()
        vm.onFormChange(vm.state.value.form.copy(width = "3"))
        vm.convert()
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_dimension_range, listOf(16, 20000)), vm.state.value.errors.width)
        assertTrue(images.shrinkCalls.isEmpty())
    }

    @Test
    fun lockedSettings_autoConvertOnPick() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onLockChange(true)
        advanceUntilIdle()
        assertTrue(settings.flow.value.locked)
        vm.onImagePicked(uri("b.jpg"))
        advanceUntilIdle()
        assertEquals(1, images.shrinkCalls.size)
        assertNotNull(vm.state.value.output)
    }

    @Test
    fun lockRefusedWhenFormInvalid() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onFormChange(vm.state.value.form.copy(maxKb = ""))
        vm.onLockChange(true)
        advanceUntilIdle()
        assertFalse(vm.state.value.form.locked)
        assertNotNull(vm.state.value.errors.maxKb)
    }

    @Test
    fun lockedFormIgnoresEdits() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onLockChange(true)
        vm.onFormChange(vm.state.value.form.copy(width = "50"))
        assertEquals("1000", vm.state.value.form.width)
    }

    @Test
    fun photoSharedDuringConversion_isQueuedNotOverwritten() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("first.jpg"))
        advanceUntilIdle()
        images.shrinkGate = CompletableDeferred()
        vm.convert()
        advanceUntilIdle()
        assertTrue(vm.state.value.busy)

        vm.onImageShared(uri("second.jpg"))
        advanceUntilIdle()
        assertEquals(listOf("first.jpg"), images.imported.map { it.lastPathSegment })
        assertTrue(vm.state.value.openRequested)

        images.shrinkGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("first.jpg", "second.jpg"), images.imported.map { it.lastPathSegment })
        assertEquals("second.jpg", vm.state.value.image!!.stem)
        assertNull("new photo clears the old result", vm.state.value.output)
    }

    @Test
    fun shrinkFailure_showsMessage() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("a.jpg"))
        advanceUntilIdle()
        images.shrinkError = ImageOpException("Can't fit this photo in 1 KB. Raise the size limit.")
        vm.convert()
        advanceUntilIdle()
        assertEquals(UiText.Raw("Can't fit this photo in 1 KB. Raise the size limit."), vm.state.value.error)
        assertNull(vm.state.value.output)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun importFailure_keepsPreviousPhoto() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("good.jpg"))
        advanceUntilIdle()
        images.importError = ImageOpException("This file isn't a supported image.")
        vm.onImagePicked(uri("bad.txt"))
        advanceUntilIdle()
        assertEquals("good.jpg", vm.state.value.image!!.stem)
        assertEquals(UiText.Raw("This file isn't a supported image."), vm.state.value.error)
    }

    @Test
    fun saveReportsSuccess() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onImagePicked(uri("a.jpg"))
        advanceUntilIdle()
        vm.convert()
        advanceUntilIdle()
        vm.saveToGallery()
        advanceUntilIdle()
        assertEquals(1, images.saved)
        assertEquals(UiText.Res(R.string.message_saved), vm.state.value.message)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }
}
