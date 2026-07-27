package com.lunentous.app.data.remote

/** Photos are served unauthenticated at {baseUrl}photos/{filename} (see
 * server/src/plugins/static.ts) -- same trust model as the SPA's own
 * static assets, since <img>/AsyncImage requests can't attach a Bearer
 * header. baseUrl already carries a trailing slash (SessionStore.normalizeBaseUrl). */
fun buildPhotoUrl(baseUrl: String?, path: String?): String? {
    if (baseUrl == null || path == null) return null
    return "$baseUrl" + "photos/" + path
}
