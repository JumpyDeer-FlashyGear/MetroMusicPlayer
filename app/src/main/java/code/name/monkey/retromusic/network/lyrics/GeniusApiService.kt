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

import code.name.monkey.retromusic.network.lyrics.dto.GeniusSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * The Genius API only ever returns *metadata* (title/artist/song URL) for a search hit,
 * never the lyrics text itself. Actual lyrics are obtained by scraping the song's page at
 * [GeniusSearchResult.url] - see [GeniusScraper]. This mirrors how github.com/shub39/Rush
 * uses Genius: search via API, fetch lyrics via scrape.
 */
interface GeniusApiService {

    @GET("search")
    suspend fun search(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String
    ): Response<GeniusSearchResponse>

    companion object {
        const val BASE_URL = "https://api.genius.com/"
    }
}
