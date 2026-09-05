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

import code.name.monkey.retromusic.network.lyrics.dto.LrcLibTrack
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApiService {

    @GET("api/search")
    suspend fun search(
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
        @Query("q") query: String? = null
    ): Response<List<LrcLibTrack>>

    companion object {
        const val BASE_URL = "https://lrclib.net/"
    }
}
