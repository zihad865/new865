package com.vx.anymaker.core.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.image.ImageRepository
import com.vx.anymaker.core.image.sanitizeStem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A file the app made, as listed on the Files tab. */
data class OutputFile(val file: File, val bytes: Long, val modified: Long) {
    val name: String get() = file.name
    val mime: String get() = mimeOf(file.name)
}

fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

/**
 * Owns the files tools produce: creates them in private storage, lists them for the Files tab,
 * shares them through the FileProvider and publishes copies to shared storage.
 */
class OutputStore(context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) {

    private val app = context.applicationContext
    val dir: File get() = File(app.filesDir, OUTPUTS_DIR)
    private val resizeResults: File get() = File(app.cacheDir, ImageRepository.RESULTS_DIR)

    /** A new, not yet existing file named after [stem] with extension [ext] (without dot). */
    fun newFile(stem: String, ext: String): File {
        val d = dir
        if (!d.isDirectory && !d.mkdirs()) throw ImageOpException("Couldn't prepare app storage.")
        val base = sanitizeStem(stem)
        var f = File(d, "$base.$ext")
        var i = 2
        while (f.exists()) {
            f = File(d, "$base-$i.$ext")
            i++
        }
        return f
    }

    /** Copies [input] into a new output file. */
    suspend fun importStream(input: InputStream, stem: String, ext: String): File = withContext(io) {
        val f = newFile(stem, ext)
        try {
            input.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        } catch (e: IOException) {
            f.delete()
            throw ImageOpException("Couldn't store the file: ${e.message}", e)
        }
        f
    }

    suspend fun importUri(uri: Uri, stem: String, ext: String): File {
        val input = withContext(io) { app.contentResolver.openInputStream(uri) }
            ?: throw ImageOpException("Couldn't open the file.")
        return importStream(input, stem, ext)
    }

    /**
     * Copies a user-chosen input (for example a PDF to edit) into private cache storage,
     * keeping its display name. Inputs never show up on the Files tab.
     */
    suspend fun importInput(uri: Uri, fallbackName: String): File = withContext(io) {
        val dir = File(app.cacheDir, INPUTS_DIR).apply { mkdirs() }
        val name = displayName(uri) ?: fallbackName
        val ext = name.substringAfterLast('.', fallbackName.substringAfterLast('.', "bin"))
        var f = File(dir, sanitizeStem(name) + "." + ext)
        var i = 2
        while (f.exists()) {
            f = File(dir, sanitizeStem(name) + "-$i." + ext)
            i++
        }
        val input = app.contentResolver.openInputStream(uri) ?: throw ImageOpException("Couldn't open the file.")
        try {
            input.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        } catch (e: IOException) {
            f.delete()
            throw ImageOpException("Couldn't open the file: ${e.message}", e)
        }
        // Keep the cache small: drop inputs older than a day.
        dir.listFiles()?.filter { it != f && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        f
    }

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (e: RuntimeException) {
        uri.lastPathSegment
    }

    suspend fun list(): List<OutputFile> = withContext(io) {
        listOf(dir, resizeResults)
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .map { OutputFile(it, it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }
    }

    suspend fun delete(file: File): Boolean = withContext(io) { file.delete() }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)

    /**
     * API 29+: copies [file] to shared storage without any permission. Images go to
     * Pictures/Anymaker, everything else to Download/Anymaker. Returns where it went.
     */
    suspend fun publish(file: File): String = withContext(io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw ImageOpException("Saving to shared storage needs Android 10 or newer.")
        }
        publishQ(file)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishQ(file: File): String {
        val mime = mimeOf(file.name)
        val isImage = mime.startsWith("image/")
        val (collection, relative) = if (isImage) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/Anymaker"
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/Anymaker"
        }
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw ImageOpException("Storage refused the file.")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw ImageOpException("Couldn't open the target file.")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is ImageOpException) throw e
            throw ImageOpException("Couldn't save: ${e.message}", e)
        }
        return relative
    }

    /** Any API: writes [file] into a document the user created with the system picker. */
    suspend fun copyTo(target: Uri, file: File): Unit = withContext(io) {
        try {
            val out = app.contentResolver.openOutputStream(target, "w") ?: throw ImageOpException("Couldn't open the chosen file.")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        } catch (e: IOException) {
            throw ImageOpException("Couldn't save: ${e.message}", e)
        } catch (e: SecurityException) {
            throw ImageOpException("Couldn't save: ${e.message}", e)
        }
    }

    companion object {
        const val OUTPUTS_DIR = "outputs"
        const val INPUTS_DIR = "inputs"
    }
}
