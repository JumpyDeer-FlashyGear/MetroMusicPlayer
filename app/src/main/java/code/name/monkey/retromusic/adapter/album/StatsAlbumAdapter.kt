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
package code.name.monkey.retromusic.adapter.album

import androidx.fragment.app.FragmentActivity
import code.name.monkey.retromusic.interfaces.IAlbumClickListener
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.util.MusicUtil

/**
 * [AlbumAdapter] as used by the Statistics "Albums" list (see CLAUDE.md, Component 3):
 * identical visuals/grid item layout, with the subtitle swapped from the album artist name
 * for a formatted playtime instead. No core [AlbumAdapter] changes were needed for this —
 * unlike [code.name.monkey.retromusic.adapter.artist.StatsArtistAdapter], `getAlbumText`
 * was already `protected open`.
 *
 * [playtimeMillisByAlbumId] is set by the caller before each [swapDataSet] — kept as a
 * plain settable map rather than a constructor `val` so [code.name.monkey.retromusic.fragments.statistics.StatsAlbumsFragment]
 * can update it and the dataset together on every time-window change without needing to
 * recreate the adapter.
 */
class StatsAlbumAdapter(
    activity: FragmentActivity,
    dataSet: List<Album>,
    itemLayoutRes: Int,
    albumClickListener: IAlbumClickListener
) : AlbumAdapter(activity, dataSet, itemLayoutRes, albumClickListener) {

    var playtimeMillisByAlbumId: Map<Long, Long> = emptyMap()

    override fun getAlbumText(album: Album): String? {
        val playedMillis = playtimeMillisByAlbumId[album.id] ?: return null
        return MusicUtil.getReadableDurationString(playedMillis)
    }
}
