package com.vx.anymaker.feature.resize

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vx.anymaker.R
import com.vx.anymaker.core.data.DataStoreResizeSettings
import com.vx.anymaker.core.data.ResizeSettingsSource
import com.vx.anymaker.core.data.appDataStore
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.image.ImageOps
import com.vx.anymaker.core.image.ImageRepository
import com.vx.anymaker.core.image.ImportedImage
import com.vx.anymaker.core.image.ShrinkOutput
import com.vx.anymaker.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResizeUiState(
    val form: ResizeForm = ResizeForm(),
    val errors: ResizeForm.Errors = ResizeForm.Errors(),
    val image: ImportedImage? = null,
    val output: ShrinkOutput? = null,
    /** Non-null while a job runs; the text says what it is doing. */
    val working: UiText? = null,
    val error: UiText? = null,
    /** One-off confirmation such as "Saved to Gallery"; cleared with [ResizeViewModel.messageShown]. */
    val message: UiText? = null,
    /** True when an outside app shared a photo and the resize screen should come forward. */
    val openRequested: Boolean = false,
    val settingsLoaded: Boolean = false,
) {
    val busy: Boolean get() = working != null
    val canConvert: Boolean get() = !busy && image != null
}

class ResizeViewModel @JvmOverloads constructor(
    app: Application,
    private val images: ImageOps = ImageRepository(app),
    private val settings: ResizeSettingsSource = DataStoreResizeSettings(app.appDataStore),
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ResizeUiState())
    val state: StateFlow<ResizeUiState> = _state.asStateFlow()

    /** Latest photo that arrived while a job was running; loaded when the job ends. */
    private var pending: Uri? = null

    init {
        viewModelScope.launch {
            val saved = settings.settings.first()
            _state.update { it.copy(form = ResizeForm.from(saved), settingsLoaded = true) }
        }
    }

    fun onFormChange(form: ResizeForm) {
        if (_state.value.form.locked) return
        _state.update { it.copy(form = form.copy(locked = false), errors = ResizeForm.Errors()) }
    }

    fun onLockChange(locked: Boolean) {
        val form = _state.value.form
        if (!locked) {
            _state.update { it.copy(form = form.copy(locked = false)) }
            persist(form.copy(locked = false))
            return
        }
        val (valid, errors) = form.validate()
        if (valid == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val lockedForm = form.copy(locked = true)
        _state.update { it.copy(form = lockedForm, errors = errors) }
        persist(lockedForm)
    }

    fun onImagePicked(uri: Uri) = load(uri, fromShare = false)

    fun onImageShared(uri: Uri) = load(uri, fromShare = true)

    private fun load(uri: Uri, fromShare: Boolean) {
        if (fromShare) _state.update { it.copy(openRequested = true) }
        if (_state.value.busy) {
            pending = uri
            return
        }
        _state.update { it.copy(working = UiText.Res(R.string.working_opening), error = null) }
        viewModelScope.launch {
            val imported = try {
                images.importToCache(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageOpException) {
                finish { it.copy(error = UiText.Raw(e.message ?: "")) }
                return@launch
            } catch (e: Exception) {
                finish { it.copy(error = UiText.Res(R.string.error_open_failed)) }
                return@launch
            }
            _state.update { it.copy(image = imported, output = null, working = null) }
            val s = _state.value
            if (s.form.locked && pending == null) convert() else drainPending()
        }
    }

    fun convert() {
        val s = _state.value
        val source = s.image ?: return
        if (s.busy) return
        val (valid, errors) = s.form.validate()
        if (valid == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        persist(s.form)
        _state.update {
            it.copy(errors = errors, output = null, error = null, working = UiText.Res(R.string.working_converting))
        }
        viewModelScope.launch {
            try {
                val out = images.shrink(source, s.form.toOptions(valid)) { msg ->
                    _state.update { st -> if (st.busy) st.copy(working = UiText.Raw(msg)) else st }
                }
                finish { it.copy(output = out) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageOpException) {
                finish { it.copy(error = UiText.Raw(e.message ?: "")) }
            } catch (e: Exception) {
                finish { it.copy(error = UiText.Res(R.string.error_convert_failed)) }
            }
        }
    }

    /** API 29+ save straight into the gallery. */
    fun saveToGallery() = runOnOutput(R.string.working_saving, R.string.message_saved) { images.saveToGallery(it) }

    /** Save into a document the user created with the system picker (all API levels). */
    fun saveTo(target: Uri) = runOnOutput(R.string.working_saving, R.string.message_saved_file) { images.writeTo(target, it) }

    fun shareUri(): Uri? = _state.value.output?.let { images.shareUri(it) }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** The system has no app to pick, create or share files; tell the user in their words. */
    fun onPickerMissing(text: String) = _state.update { it.copy(error = UiText.Raw(text)) }

    fun openHandled() = _state.update { it.copy(openRequested = false) }

    private fun runOnOutput(working: Int, done: Int, block: suspend (ShrinkOutput) -> Unit) {
        val out = _state.value.output ?: return
        if (_state.value.busy) return
        _state.update { it.copy(working = UiText.Res(working), error = null) }
        viewModelScope.launch {
            try {
                block(out)
                finish { it.copy(message = UiText.Res(done)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageOpException) {
                finish { it.copy(error = UiText.Raw(e.message ?: "")) }
            } catch (e: Exception) {
                finish { it.copy(error = UiText.Res(R.string.error_save_failed)) }
            }
        }
    }

    private fun finish(change: (ResizeUiState) -> ResizeUiState) {
        _state.update { change(it).copy(working = null) }
        drainPending()
    }

    private fun drainPending() {
        val next = pending ?: return
        pending = null
        load(next, fromShare = false)
    }

    private fun persist(form: ResizeForm) {
        val (valid, _) = form.validate()
        if (valid == null) return
        viewModelScope.launch { settings.save(form.toSettings(valid)) }
    }
}
