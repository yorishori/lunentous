package com.lunentous.app.ui.photos

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Copies an image's content -- whether from a share-to-app intent or the
 * gallery picker, the source content:// URI is only guaranteed readable
 * for the lifetime of the request that handed it over -- into app-private
 * storage as a durable File, so it can flow through the same
 * pendingPhotos/outbox path a camera capture already uses (see
 * ui/camera/CameraCapture.kt's createCaptureFile, same photos/ dir).
 */
fun importImageToLocalFile(context: Context, uri: Uri): File? {
    val resolver = context.contentResolver
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(photosDir, "imported_${System.currentTimeMillis()}.$extension")
    return runCatching {
        val input = resolver.openInputStream(uri) ?: return null
        input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
        file
    }.getOrNull()
}
