package com.nomad.travel.data.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the on-disk lifecycle of chat-attached images. Picker / camera URIs
 * are not stable across process restarts, so messages keep a copy here under
 * `filesDir/chat_images/`. Files are removed when their owning session is
 * deleted.
 */
internal object ChatImageStore {
    private const val DIR = "chat_images"
    private const val PREFIX = "img_"

    private fun root(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Copies [source] into app-internal storage and returns a stable
     *  FileProvider URI, or `null` if the copy fails. */
    suspend fun persist(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val target = File(root(context), "$PREFIX${UUID.randomUUID()}.jpg")
        try {
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output); true }
            } ?: false
            if (!copied) return@withContext null
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
        } catch (t: Throwable) {
            target.delete()
            null
        }
    }

    /** Deletes the file backing [uri] when it belongs to our managed dir. */
    suspend fun delete(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return@withContext
        if (!name.startsWith(PREFIX)) return@withContext
        val target = File(root(context), name)
        val rootPath = root(context).canonicalPath
        if (target.exists() && target.parentFile?.canonicalPath == rootPath) {
            target.delete()
        }
    }

    /** Wipes every persisted chat image. Used on bulk session delete. */
    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        root(context).listFiles()?.forEach { it.delete() }
    }
}
