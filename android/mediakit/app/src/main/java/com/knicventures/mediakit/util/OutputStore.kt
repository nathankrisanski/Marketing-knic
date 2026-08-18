package com.knicventures.mediakit.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** A file that landed somewhere the user can actually find it. */
data class SavedFile(val uri: Uri, val displayName: String, val locationLabel: String)

/**
 * Publishes finished artefacts to shared storage.
 *
 * On Android 10+ this goes through MediaStore, so videos show up in the gallery
 * and transcripts in Documents with no storage permission at all. Older
 * releases fall back to the public directories via WRITE_EXTERNAL_STORAGE.
 */
object OutputStore {

    private const val FOLDER = "MediaKit"

    fun saveVideo(context: Context, source: File, displayName: String): SavedFile =
        save(
            context = context,
            source = source,
            displayName = displayName,
            mimeType = "video/mp4",
            collection = Collection.MOVIES,
        )

    fun saveMarkdown(context: Context, content: String, displayName: String): SavedFile {
        val temp = File.createTempFile("transcript", ".md", context.cacheDir)
        temp.writeText(content)
        return try {
            save(
                context = context,
                source = temp,
                displayName = displayName,
                mimeType = "text/markdown",
                collection = Collection.DOCUMENTS,
            )
        } finally {
            temp.delete()
        }
    }

    private enum class Collection(val legacyDir: String, val relativePath: String) {
        MOVIES(Environment.DIRECTORY_MOVIES, "${Environment.DIRECTORY_MOVIES}/$FOLDER"),
        DOCUMENTS(Environment.DIRECTORY_DOCUMENTS, "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER"),
    }

    private fun save(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        collection: Collection,
    ): SavedFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = when (collection) {
                Collection.MOVIES -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                Collection.DOCUMENTS -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, collection.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(contentUri, values)
                ?: throw IOException("Could not create $displayName in shared storage.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out, 1 shl 16) }
                } ?: throw IOException("Could not write to $displayName.")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return SavedFile(uri, displayName, collection.relativePath)
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(collection.legacyDir), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create ${dir.absolutePath}")
        }
        val target = uniqueFile(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        }
        return SavedFile(Uri.fromFile(target), target.name, dir.absolutePath)
    }

    private fun uniqueFile(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        var candidate = File(dir, displayName)
        var counter = 1
        while (candidate.exists()) {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            candidate = File(dir, "$base ($counter)$suffix")
            counter++
        }
        return candidate
    }

    /** Strips characters that are illegal in filenames on shared storage. */
    fun sanitizeFileName(raw: String, fallback: String = "media", maxLength: Int = 80): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.take(maxLength).ifBlank { fallback }
    }
}
