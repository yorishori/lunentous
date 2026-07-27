package com.lunentous.app.data.remote

import com.lunentous.app.data.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the bearer token to every request, mirroring the server's
 * requireApiKey hook (server/src/lib/auth.ts). If no token is stored the
 * request just goes out unauthenticated and the server rejects it with a
 * 401 -- there's no meaningful client-side check to short-circuit here
 * beyond what SessionStore.hasSession() already lets repositories check
 * before ever attempting a call. */
class ApiKeyInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = sessionStore.getToken()
        val request = if (token != null) {
            original.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
