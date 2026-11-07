package com.example.messenger.crypto

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * One directory per attachment id under cacheDir/media.
 * The id is a UUID, so a display name cannot escape that directory.
 */
class MediaFileCache(context: Context) {
    private val root: File = File(context.cacheDir, "media")

    fun existing(attachmentId: String): File? {
        val dir = directory(attachmentId) ?: return null
        if (!dir.isDirectory) return null
        return dir.listFiles()?.singleOrNull { it.isFile }
    }

    fun write(attachmentId: String, displayName: String, bytes: ByteArray): File {
        val dir = directory(attachmentId)
            ?: throw IllegalArgumentException("Attachment id must be a UUID.")
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Couldn't create the media cache.")
        }
        val target = File(dir, cacheName(displayName))
        val canonicalDir = dir.canonicalFile
        if (target.canonicalFile.parentFile != canonicalDir) {
            throw IllegalArgumentException("Media name is not valid.")
        }
        dir.listFiles()?.forEach { child ->
            if (child.name != target.name) child.deleteRecursively()
        }
        if (target.isDirectory) target.deleteRecursively()
        target.writeBytes(bytes)
        return target
    }

    private fun directory(attachmentId: String): File? {
        if (!ATTACHMENT_ID.matches(attachmentId)) return null
        return File(root, attachmentId)
    }

    private fun cacheName(displayName: String): String {
        val name = sanitizeMediaName(displayName)
        return if (name == "." || name == "..") "file" else name
    }

    private companion object {
        val ATTACHMENT_ID = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }
}
