/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.network.lyrics

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Small, self-contained Retrofit setup for lyrics lookups only. Nothing else in the app
 * currently uses the Retrofit/Gson dependencies (they were declared but unused before this
 * feature), so there is no existing network-client convention to fold this into.
 */
object LyricsClient {

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", LYRICS_USER_AGENT)
            .build()
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val geniusApi: GeniusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GeniusApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeniusApiService::class.java)
    }

    val lrcLibApi: LrcLibApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LrcLibApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApiService::class.java)
    }

    /** Exposed so [GeniusScraper] can share the same User-Agent when fetching song pages. */
    fun userAgent(): String = LYRICS_USER_AGENT
}
