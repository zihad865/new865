package com.vx.anymaker.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.vx.anymaker.R
import com.vx.anymaker.core.files.OutputStore
import com.vx.anymaker.core.files.mimeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** ACTION_CREATE_DOCUMENT with the MIME type chosen at launch time. Input: (mime, suggested name). */
class CreateDocumentFor : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Saves and shares tool outputs, reporting the outcome in the screen's snackbar. */
class ExportController internal constructor(
    private val context: Context,
    private val store: OutputStore,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val messages: (Int, String?) -> String,
    private val createDocument: (Pair<String, String>) -> Unit,
) {
    internal var pendingFile: File? = null

    fun save(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val text = try {
                    messages(R.string.message_saved_to, store.publish(file))
                } catch (e: Exception) {
                    e.message ?: messages(R.string.error_save_failed, null)
                }
                snackbar.showSnackbar(text)
            }
        } else {
            pendingFile = file
            try {
                createDocument(mimeOf(file.name) to file.name)
            } catch (e: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(messages(R.string.error_no_picker, null)) }
            }
        }
    }

    fun saveAll(files: List<File>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                var saved = 0
                var where = ""
                files.forEach { f -> runCatching { store.publish(f) }.onSuccess { saved++; where = it } }
                snackbar.showSnackbar(
                    if (saved == files.size) messages(R.string.message_saved_to, where)
                    else messages(R.string.message_saved_some, "$saved/${files.size}"),
                )
            }
        } else {
            share(files)
        }
    }

    internal fun onDocumentCreated(target: Uri?) {
        val file = pendingFile ?: return
        pendingFile = null
        if (target == null) return
        scope.launch {
            val text = try {
                store.copyTo(target, file)
                messages(R.string.message_saved_file, null)
            } catch (e: Exception) {
                e.message ?: messages(R.string.error_save_failed, null)
            }
            snackbar.showSnackbar(text)
        }
    }

    fun share(files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { store.shareUri(it) })
        val mimes = files.map { mimeOf(it.name) }.distinct()
        val mime = if (mimes.size == 1) mimes[0] else if (mimes.all { it.startsWith("image/") }) "image/*" else "*/*"
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mime
        val clip = ClipData.newRawUri(files[0].name, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        send.clipData = clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, messages(R.string.share_chooser, null)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(messages(R.string.error_no_picker, null)) }
        }
    }

    fun share(file: File) = share(listOf(file))

    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(Intent.createChooser(send, messages(R.string.share_chooser, null)))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(messages(R.string.error_no_picker, null)) }
        }
    }
}

@Composable
fun rememberExportController(snackbar: SnackbarHostState): ExportController {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val store = remember { OutputStore(context) }
    var controller: ExportController? = null
    val launcher = rememberLauncherForActivityResult(CreateDocumentFor()) { uri -> controller?.onDocumentCreated(uri) }
    controller = remember(snackbar) {
        ExportController(
            context = context,
            store = store,
            scope = scope,
            snackbar = snackbar,
            messages = { id, arg -> if (arg == null) resources.getString(id) else resources.getString(id, arg) },
            createDocument = { launcher.launch(it) },
        )
    }
    return controller
}
