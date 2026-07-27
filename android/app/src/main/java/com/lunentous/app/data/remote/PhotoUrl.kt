package com.lunentous.app.data.remote

import com.lunentous.app.data.local.entity.PhotoEntity
import java.io.File

/** Photos are served unauthenticated at {baseUrl}photos/{filename} (see
 * server/src/plugins/static.ts) -- same trust model as the SPA's own
 * static assets, since <img>/AsyncImage requests can't attach a Bearer
 * header. baseUrl already carries a trailing slash (SessionStore.normalizeBaseUrl). */
fun buildPhotoUrl(baseUrl: String?, path: String?): String? {
    if (baseUrl == null || path == null) return null
    return "$baseUrl" + "photos/" + path
}

/** Coil's `model` param accepts a String URL or a File equally well, so
 * this can feed AsyncImage directly. Falls back to the on-device capture
 * file when a photo hasn't synced yet (no remoteFilePath) -- otherwise a
 * just-captured, still-offline photo would show nothing until it uploads. */
fun photoDisplayModel(baseUrl: String?, photo: PhotoEntity): Any? {
    buildPhotoUrl(baseUrl, photo.remoteFilePath)?.let { return it }
    return photo.localFileUri?.let { File(it) }
}
