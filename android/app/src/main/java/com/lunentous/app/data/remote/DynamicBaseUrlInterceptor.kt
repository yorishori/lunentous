package com.lunentous.app.data.remote

import com.lunentous.app.data.auth.SessionStore
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** Thrown when a network call is attempted with no server configured --
 * repositories should generally check SessionStore.hasSession() before
 * calling the API at all and never trigger this in practice, but it's a
 * clear failure mode if that check is ever missed. */
class NoServerConfiguredException : IOException("No server configured")

/**
 * Retrofit's base URL is normally fixed at build time; this app's server
 * address is a runtime Settings value instead. Rather than rebuilding the
 * whole Retrofit/OkHttp stack every time the user connects/reconnects,
 * LunentousApi is built once against PLACEHOLDER_BASE_URL and this
 * interceptor rewrites every outgoing request's scheme/host/port/path
 * prefix to the currently configured server just before it goes out.
 */
class DynamicBaseUrlInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configured = sessionStore.getBaseUrl()?.toHttpUrlOrNull()
            ?: throw NoServerConfiguredException()

        val rewritten = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .encodedPath(configured.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }

    companion object {
        /** Never actually contacted -- exists only so Retrofit.Builder has a
         * syntactically valid baseUrl to build endpoint paths against before
         * this interceptor rewrites them. */
        const val PLACEHOLDER_BASE_URL = "http://localhost/"
    }
}
