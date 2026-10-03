package com.vx.anymaker.feature.common

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vx.anymaker.R
import com.vx.anymaker.core.files.OutputStore
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.pdf.PdfPasswordException
import com.vx.anymaker.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base for tool screens: one job at a time, a progress text while it runs, and errors
 * mapped to messages. State is Compose snapshot state, written on the main thread.
 */
abstract class ToolViewModel(app: Application) : AndroidViewModel(app) {

    protected val store = OutputStore(app)

    /** Non-null while a job runs. */
    var working by mutableStateOf<UiText?>(null)
        protected set
    var error by mutableStateOf<UiText?>(null)
        protected set

    val busy: Boolean get() = working != null

    private var job: Job? = null

    /** Runs [block] on the main thread; it switches to background dispatchers itself for heavy work. */
    protected fun work(label: UiText = UiText.Res(R.string.working), block: suspend () -> Unit) {
        if (busy) return
        working = label
        error = null
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageOpException) {
                error = UiText.Raw(e.message ?: "")
            } catch (e: PdfPasswordException) {
                error = UiText.Res(R.string.pdf_wrong_password)
            } catch (e: OutOfMemoryError) {
                error = UiText.Raw("Not enough memory for this file.")
            } catch (e: Exception) {
                error = UiText.Res(R.string.error_generic)
            } finally {
                working = null
            }
        }
    }

    protected fun progress(label: UiText) {
        if (busy) working = label
    }

    fun showError(text: UiText) {
        error = text
    }

    fun clearError() {
        error = null
    }
}
