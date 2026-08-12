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
package code.name.monkey.retromusic.adapter.artist

import androidx.fragment.app.FragmentActivity
import code.name.monkey.retromusic.interfaces.IArtistClickListener
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.util.MusicUtil

/**
 * [ArtistAdapter] as used by the Statistics "Artists" list (see CLAUDE.md, Component 3):
 * identical visuals/grid item layout, with the subtitle swapped for a formatted playtime
 * instead of being hidden.
 *
 * [playtimeMillisByArtistId] is set by the caller before each [swapDataSet] — kept as a
 * plain settable map rather than a constructor `val` so [code.name.monkey.retromusic.fragments.statistics.StatsArtistsFragment]
 * can update it and the dataset together on every time-window change without needing to
 * recreate the adapter.
 */
class StatsArtistAdapter(
    activity: FragmentActivity,
    dataSet: List<Artist>,
    itemLayoutRes: Int,
    artistClickListener: IArtistClickListener
) : ArtistAdapter(activity, dataSet, itemLayoutRes, artistClickListener) {

    var playtimeMillisByArtistId: Map<Long, Long> = emptyMap()

    override fun getArtistText(artist: Artist): String? {
        val playedMillis = playtimeMillisByArtistId[artist.id] ?: return null
        return MusicUtil.getReadableDurationString(playedMillis)
    }
}
