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

import android.util.Log
import code.name.monkey.retromusic.network.lyrics.dto.LrcLibTrack
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks up lyrics online: LRCLIB first (it can return synced *and* plain lyrics with no
 * scraping needed), falling back to a Genius search + page-scrape for plain lyrics only when
 * LRCLIB has nothing. Same ordering github.com/shub39/Rush's RushRepository.fetchSong uses,
 * for the same reason - LRCLIB is a clean API and Genius scraping is inherently more fragile.
 *
 * The Genius token is per-user (see [PreferenceUtil.geniusApiToken], set in Settings), not a
 * token shipped with the app - see the discussion this decision came out of. With no token
 * set, the Genius search call is simply skipped and this falls through to returning null when
 * LRCLIB also has nothing; nothing crashes, Genius just contributes nothing until the user
 * supplies their own token.
 */
object OnlineLyricsRepository {

    private const val TAG = "OnlineLyricsRepository"

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        fetchFromLrcLib(title, artist, album)
            ?: fetchFromGenius(title, artist)
    }

    private suspend fun fetchFromLrcLib(
        title: String,
        artist: String,
        album: String?
    ): OnlineLyricsResult? {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val strategies: List<suspend () -> List<LrcLibTrack>> = listOf(
            { queryLrcLib(trackName = cleanedTitle, artistName = cleanedArtist, albumName = album) },
            { queryLrcLib(trackName = cleanedTitle) },
            { queryLrcLib(query = "$cleanedArtist $cleanedTitle") },
            { queryLrcLib(query = cleanedTitle) },
            { queryLrcLib(trackName = title.trim(), artistName = artist.trim()) }
        )

        for (strategy in strategies) {
            val match = strategy()
                .firstOrNull { it.plainLyrics != null || it.syncedLyrics != null }
            if (match != null) {
                return OnlineLyricsResult(
                    source = LyricsSource.LRCLIB,
                    plainLyrics = match.plainLyrics,
                    syncedLyrics = match.syncedLyrics
                )
            }
        }
        return null
    }

    private suspend fun queryLrcLib(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null
    ): List<LrcLibTrack> {
        return try {
            val response = LyricsClient.lrcLibApi.search(
                trackName = trackName,
                artistName = artistName,
                albumName = albumName,
                query = query
            )
            if (response.isSuccessful) response.body().orEmpty() else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB search failed", e)
            emptyList()
        }
    }

    private suspend fun fetchFromGenius(title: String, artist: String): OnlineLyricsResult? {
        val token = PreferenceUtil.geniusApiToken
        if (token.isBlank()) return null

        val songUrl = try {
            val response = LyricsClient.geniusApi.search(
                bearerToken = "Bearer $token",
                query = "$artist ${cleanTitle(title)}"
            )
            if (!response.isSuccessful) return null

            response.body()?.response?.hits
                ?.firstOrNull { it.type == "song" }
                ?.result?.url
        } catch (e: Exception) {
            Log.e(TAG, "Genius search failed", e)
            null
        } ?: return null

        val lyrics = GeniusScraper.scrape(songUrl) ?: return null
        return OnlineLyricsResult(
            source = LyricsSource.GENIUS,
            plainLyrics = lyrics,
            syncedLyrics = null
        )
    }

    private val bracketPatterns = listOf(
        Regex("""\s*\(.*?\)"""),
        Regex("""\s*\[.*?]""")
    )

    private val titleCleanupPatterns = bracketPatterns + listOf(
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE)
    )

    private val artistSeparators = listOf(
        " & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with "
    )

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        titleCleanupPatterns.forEach { cleaned = cleaned.replace(it, "") }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        bracketPatterns.forEach { cleaned = cleaned.replace(it, "") }
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }
}
