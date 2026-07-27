package com.lunentous.app.ui.share

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Copies a shared image's content -- the source app's content:// URI is
 * only readable for the lifetime of this Intent -- into app-private
 * storage as a durable File, so it can flow through the same
 * pendingPhotos/outbox path a camera capture already uses (see
 * ui/camera/CameraCapture.kt's createCaptureFile, same photos/ dir).
 */
fun importSharedImage(context: Context, uri: Uri): File? {
    val resolver = context.contentResolver
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(photosDir, "shared_${System.currentTimeMillis()}.$extension")
    return runCatching {
        val input = resolver.openInputStream(uri) ?: return null
        input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
        file
    }.getOrNull()
}
