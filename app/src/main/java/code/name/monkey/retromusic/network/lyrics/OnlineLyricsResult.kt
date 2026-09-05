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

enum class LyricsSource {
    LRCLIB,
    GENIUS
}

/**
 * At least one of [plainLyrics]/[syncedLyrics] is non-null whenever this exists.
 * [syncedLyrics] is LRC-formatted text, ready to hand to [code.name.monkey.retromusic.util.LyricUtil]
 * exactly the way manually-pasted synced lyrics are.
 */
data class OnlineLyricsResult(
    val source: LyricsSource,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
