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
package code.name.monkey.retromusic.fragments.statistics

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentStatsArtistDetailBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.model.stats.AlbumStat
import code.name.monkey.retromusic.model.stats.SongStat
import code.name.monkey.retromusic.repository.RealRepository
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.stats.StatsRowBinder
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
/**
 * Artist detail stats screen (see CLAUDE.md, Component 4; redesigned in Component 6's pivot
 * away from charts). Opened by tapping an artist in Component 3's `StatsArtistsFragment`.
 *
 * No charts, no time window anymore -- just three plain sections, all real data rendered as
 * ranked text lists via [StatsRowBinder]:
 * - **Overview**: Total Playtime (real, sum of [Artist.songs]' real playtime), Discography
 *   length (real -- sum of every song's actual [code.name.monkey.retromusic.model.Song.duration]
 *   via `artist.songs`), Albums ([Artist.albumCount]), Songs ([Artist.songCount]).
 * - **Top Albums**: every album by this artist ([Artist.albums], uncapped), ranked
 *   descending by real playtime (summed from that album's songs).
 * - **Top Songs**: every song by this artist ([Artist.songs]), ranked descending by real
 *   playtime, capped at [MAX_TOP_SONGS].
 *
 * Both ranked lists use the same 3-tier sizing as the main screen's Top Genres list (rank 1
 * big, ranks 2-3 medium, rank 4+ small) -- confirmed for every ranked list on these screens,
 * see CLAUDE.md.
 *
 * Phase B (Component 7): playtime is real now, resolved the same way this screen's own
 * Overview total-playtime field, the Genre Detail screen, and the "most played" screen all
 * resolve it -- [RealRepository.songsWithPlayTime] decorates [Artist.songs] with each
 * song's real `PlayCountEntity.playTime` in one query; Top Albums sums that per album,
 * Top Songs reads it per song directly. The artist itself is real, found by id in
 * [libraryViewModel]'s artist list -- same lookup-by-id pattern Component 3 used for album
 * taps.
 */
class StatsArtistDetailFragment : AbsMainActivityFragment(R.layout.fragment_stats_artist_detail) {

    private var _binding: FragmentStatsArtistDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<StatsArtistDetailFragmentArgs>()
    private var currentArtist: Artist? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatsArtistDetailBinding.bind(view)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = args.artistName
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        libraryViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            currentArtist = artists.firstOrNull { it.id == args.artistId }
            render()
        }
    }

    private fun render() {
        val artist = currentArtist ?: return
        lifecycleScope.launch {
            // One query decorates every song in the discography with its real playtime;
            // Overview and Top Songs read it directly, Top Albums sums it per album.
            val songsWithPlayTime = withContext(IO) {
                get<RealRepository>().songsWithPlayTime(artist.songs)
            }
            renderOverview(artist, songsWithPlayTime)
            renderTopAlbums(artist, songsWithPlayTime)
            renderTopSongs(songsWithPlayTime)
        }
    }

    private fun renderOverview(artist: Artist, songsWithPlayTime: List<Song>) {
        val totalPlaytimeMillis = songsWithPlayTime.sumOf { it.playTime }
        val discographyLengthMillis = artist.songs.sumOf { it.duration }

        binding.overviewContainer.removeAllViews()
        val inflater = layoutInflater
        val container = binding.overviewContainer
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.stats_total_playtime),
            MusicUtil.getReadableDurationString(totalPlaytimeMillis)
        )
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.stats_discography_length),
            MusicUtil.getReadableDurationString(discographyLengthMillis)
        )
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.albums), artist.albumCount.toString()
        )
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.songs), artist.songCount.toString()
        )
    }

    private fun renderTopAlbums(artist: Artist, songsWithPlayTime: List<Song>) {
        val playTimeByAlbumId = songsWithPlayTime.groupBy { it.albumId }
            .mapValues { (_, songs) -> songs.sumOf { it.playTime } }
        val stats = artist.albums
            .map { album ->
                AlbumStat(
                    id = album.id,
                    name = album.title,
                    playedMillis = playTimeByAlbumId[album.id] ?: 0L
                )
            }
            .sortedByDescending { it.playedMillis }

        binding.topAlbumsContainer.removeAllViews()
        stats.forEachIndexed { index, stat ->
            StatsRowBinder.addRankRow(
                layoutInflater, binding.topAlbumsContainer, index + 1, stat.name, stat.playedMillis
            )
        }
    }

    private fun renderTopSongs(songsWithPlayTime: List<Song>) {
        val stats = songsWithPlayTime
            .map { song -> SongStat(id = song.id, title = song.title, playedMillis = song.playTime) }
            .sortedByDescending { it.playedMillis }
            .take(MAX_TOP_SONGS)

        binding.topSongsContainer.removeAllViews()
        stats.forEachIndexed { index, stat ->
            StatsRowBinder.addRankRow(
                layoutInflater, binding.topSongsContainer, index + 1, stat.title, stat.playedMillis
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        // No screen-specific menu items -- same as Component 3's list screens.
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = false

    private companion object {
        /** Top Songs is capped, unlike Top Albums -- confirmed at 15, see CLAUDE.md. */
        const val MAX_TOP_SONGS = 15
    }
}
