package com.lunentous.app.data.remote

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds the app's single long-lived Retrofit/OkHttp stack. One instance
 * for the whole app lifetime is enough -- DynamicBaseUrlInterceptor and
 * ApiKeyInterceptor both read SessionStore fresh on every request, so
 * connecting/disconnecting/reconfiguring the server never requires
 * rebuilding this.
 */
object NetworkModule {
    fun createGson(): Gson = Gson()

    fun createApi(sessionStore: SessionStore, gson: Gson = createGson()): LunentousApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(sessionStore))
            .addInterceptor(ApiKeyInterceptor(sessionStore))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(DynamicBaseUrlInterceptor.PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LunentousApi::class.java)
    }
}
