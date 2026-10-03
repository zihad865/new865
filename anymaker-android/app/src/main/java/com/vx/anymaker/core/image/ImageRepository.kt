package com.vx.anymaker.core.image

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class ImageRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ImageOps {

    private val app = context.applicationContext
    private val sourcesDir get() = File(app.cacheDir, SOURCES_DIR)
    private val resultsDir get() = File(app.cacheDir, RESULTS_DIR)

    override suspend fun importToCache(uri: Uri): ImportedImage = withContext(io) {
        val dir = sourcesDir.ensureDir()
        val dest = File(dir, "src-${UUID.randomUUID()}.img")
        var bytes = 0L
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw ImageOpException("Couldn't open this photo.")
            input.use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        bytes += n
                    }
                }
            }
        } catch (e: ImageOpException) {
            dest.delete()
            throw e
        } catch (e: IOException) {
            dest.delete()
            throw ImageOpException("Couldn't open this photo: ${e.message}", e)
        } catch (e: SecurityException) {
            dest.delete()
            throw ImageOpException("Anymaker isn't allowed to read this photo.", e)
        }
        val dims = Shrinker.orientedBounds(dest)
        if (dims == null) {
            dest.delete()
            throw ImageOpException("This file isn't a supported image.")
        }
        prune(dir, KEEP_SOURCES, keep = dest)
        ImportedImage(dest, sanitizeStem(displayName(uri)), bytes, dims[0], dims[1])
    }

    override suspend fun shrink(
        source: ImportedImage,
        options: Shrinker.Options,
        onProgress: (String) -> Unit,
    ): ShrinkOutput {
        val result = withContext(compute) {
            try {
                Shrinker.run(source.file, options) { onProgress(it) }
            } catch (e: Shrinker.ShrinkException) {
                throw ImageOpException(e.message ?: "Conversion failed.", e)
            } catch (e: OutOfMemoryError) {
                throw ImageOpException("Not enough memory. Try fewer pixels.")
            }
        }
        return withContext(io) {
            val dir = resultsDir.ensureDir()
            val base = "${source.stem}_${result.width}x${result.height}"
            val ext = result.extension()
            var out = File(dir, base + ext)
            var i = 2
            while (out.exists()) {
                out = File(dir, "$base-$i$ext")
                i++
            }
            try {
                FileOutputStream(out).use { it.write(result.data) }
            } catch (e: IOException) {
                out.delete()
                throw ImageOpException("Couldn't store the result: ${e.message}", e)
            }
            prune(dir, KEEP_RESULTS, keep = out)
            ShrinkOutput(
                file = out,
                width = result.width,
                height = result.height,
                requestedWidth = result.requestedWidth,
                requestedHeight = result.requestedHeight,
                bytes = out.length(),
                quality = result.quality,
                webp = result.webp,
            )
        }
    }

    override suspend fun saveToGallery(output: ShrinkOutput): Unit = withContext(io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw ImageOpException("Saving to the gallery needs Android 10 or newer.")
        }
        insertIntoGallery(output)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoGallery(output: ShrinkOutput) {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, output.file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, output.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, GALLERY_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw ImageOpException("The gallery refused the file.")
        try {
            val out = resolver.openOutputStream(uri, "w")
                ?: throw ImageOpException("Couldn't open the gallery file.")
            out.use { stream -> output.file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is ImageOpException) throw e
            throw ImageOpException("Couldn't save: ${e.message}", e)
        }
    }

    override suspend fun writeTo(target: Uri, output: ShrinkOutput): Unit = withContext(io) {
        try {
            val out = app.contentResolver.openOutputStream(target, "w")
                ?: throw ImageOpException("Couldn't open the chosen file.")
            out.use { stream -> output.file.inputStream().use { it.copyTo(stream) } }
        } catch (e: IOException) {
            throw ImageOpException("Couldn't save: ${e.message}", e)
        } catch (e: SecurityException) {
            throw ImageOpException("Couldn't save: ${e.message}", e)
        }
    }

    override fun shareUri(output: ShrinkOutput): Uri =
        FileProvider.getUriForFile(app, "${app.packageName}.files", output.file)

    private fun displayName(uri: Uri): String? {
        val fromResolver = try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "display name lookup failed", e)
            null
        }
        return fromResolver?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment
    }

    private fun File.ensureDir(): File {
        if (!isDirectory && !mkdirs()) throw ImageOpException("Couldn't prepare app storage.")
        return this
    }

    /** Keeps the newest [count] files so earlier shares stay readable; never deletes [keep]. */
    private fun prune(dir: File, count: Int, keep: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= count) return
        files.filter { it != keep }
            .sortedByDescending { it.lastModified() }
            .drop(count - 1)
            .forEach { if (!it.delete()) Log.w(TAG, "could not delete $it") }
    }

    companion object {
        private const val TAG = "Anymaker"
        const val SOURCES_DIR = "sources"
        const val RESULTS_DIR = "results"
        const val GALLERY_DIR = "Pictures/Anymaker"
        private const val KEEP_SOURCES = 3
        private const val KEEP_RESULTS = 10
    }
}
