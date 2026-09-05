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
package code.name.monkey.retromusic.network.lyrics.dto

import com.google.gson.annotations.SerializedName

data class GeniusSearchResponse(
    val response: GeniusSearchInnerResponse
)

data class GeniusSearchInnerResponse(
    val hits: List<GeniusHit>
)

data class GeniusHit(
    val type: String,
    val result: GeniusSearchResult
)

data class GeniusSearchResult(
    val id: Long,
    val title: String,
    @SerializedName("artist_names") val artistNames: String,
    @SerializedName("url") val url: String,
    @SerializedName("song_art_image_url") val songArtImageUrl: String? = null,
    val instrumental: Boolean = false
)
