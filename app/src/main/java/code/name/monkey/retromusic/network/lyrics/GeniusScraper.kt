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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Genius's public API never returns lyrics text, only song metadata + a page URL - so lyrics
 * have to be scraped from the rendered song page itself. Same approach (and the same general
 * markup target, `div[data-lyrics-container='true']`) as github.com/shub39/Rush's
 * GeniusScraper. Genius has changed this markup before to break scrapers (see their tracker,
 * shub39/Rush#68), so this is inherently more fragile than the LRCLIB path and is only used
 * as a fallback when LRCLIB has nothing.
 */
object GeniusScraper {

    private const val TAG = "GeniusScraper"

    private val nonLyricsLines = listOf(
        Regex("^\\d+\\s*Contributors?.*", RegexOption.IGNORE_CASE),
        Regex("^Translations.*", RegexOption.IGNORE_CASE),
        Regex("^Read More.*", RegexOption.IGNORE_CASE),
        Regex("^.*Lyrics$")
    )

    fun scrape(songUrl: String): String? {
        return try {
            val document = Jsoup.connect(songUrl)
                .userAgent(LyricsClient.userAgent())
                .timeout(15_000)
                .get()

            val lyricsContainers = document.select("div[data-lyrics-container='true']")
            if (lyricsContainers.isEmpty()) return null

            val lyrics = buildString {
                lyricsContainers.forEach { container ->
                    container.childNodes().forEach { node ->
                        val text = when (node) {
                            is TextNode -> node.text()
                            is Element -> if (node.tagName() == "br") "\n" else node.wholeText()
                            else -> return@forEach
                        }.trim()

                        if (text.isBlank()) return@forEach
                        if (nonLyricsLines.any { it.matches(text) }) return@forEach

                        if (text.startsWith("[") || text.endsWith("]")) append("\n")
                        append("\n$text")
                    }
                }
            }.trim()

            lyrics.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape lyrics from $songUrl", e)
            null
        }
    }
}
