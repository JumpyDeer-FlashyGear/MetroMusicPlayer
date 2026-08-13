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
import code.name.monkey.retromusic.databinding.FragmentStatsAlbumDetailBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Song
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
 * Album detail stats screen (see CLAUDE.md, Component 5; redesigned in Component 6's pivot
 * away from charts). Opened by tapping an album in Component 3's `StatsAlbumsFragment`.
 *
 * A strict subset of [StatsArtistDetailFragment]'s structure -- same Overview block, minus
 * the "Albums" row (this screen is scoped to a single album, so that count doesn't apply)
 * and with "Discography length" renamed "Album length", plus a single ranked list: **Top
 * Songs**, which (unlike the artist screen's capped-at-15 version) shows **every** song on
 * this album, since the brief (see CLAUDE.md, Component 5) asks for the full song list here.
 *
 * Unlike the artist screen, there's no further per-song breakdown chart here — the brief
 * (see CLAUDE.md, Component 5) only asks for the time-series view, so this screen is
 * intentionally a strict subset of [StatsArtistDetailFragment] (same toolbar/time-window/
 * chart-card structure, minus the by-album pie section), not a copy with a section removed
 * after the fact.
 *
 * Phase B (Component 7): playtime is real now, resolved the same way
 * [StatsArtistDetailFragment] resolves it -- [RealRepository.songsWithPlayTime] decorates
 * [Album.songs] with each song's real `PlayCountEntity.playTime` in one query. The album
 * itself is real, found by id in [libraryViewModel]'s album list -- same lookup-by-id
 * pattern Component 4 used for its artist lookup.
 */
class StatsAlbumDetailFragment : AbsMainActivityFragment(R.layout.fragment_stats_album_detail) {

    private var _binding: FragmentStatsAlbumDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<StatsAlbumDetailFragmentArgs>()
    private var currentAlbum: Album? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatsAlbumDetailBinding.bind(view)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = args.albumName
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        libraryViewModel.getAlbums().observe(viewLifecycleOwner) { albums ->
            currentAlbum = albums.firstOrNull { it.id == args.albumId }
            render()
        }
    }

    private fun render() {
        val album = currentAlbum ?: return
        lifecycleScope.launch {
            val songsWithPlayTime = withContext(IO) {
                get<RealRepository>().songsWithPlayTime(album.songs)
            }
            renderOverview(album, songsWithPlayTime)
            renderTopSongs(songsWithPlayTime)
        }
    }

    private fun renderOverview(album: Album, songsWithPlayTime: List<Song>) {
        val totalPlaytimeMillis = songsWithPlayTime.sumOf { it.playTime }
        val albumLengthMillis = album.songs.sumOf { it.duration }

        binding.overviewContainer.removeAllViews()
        val inflater = layoutInflater
        val container = binding.overviewContainer
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.stats_total_playtime),
            MusicUtil.getReadableDurationString(totalPlaytimeMillis)
        )
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.stats_album_length),
            MusicUtil.getReadableDurationString(albumLengthMillis)
        )
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.songs), album.songCount.toString()
        )
    }

    private fun renderTopSongs(songsWithPlayTime: List<Song>) {
        val stats = songsWithPlayTime
            .map { song -> SongStat(id = song.id, title = song.title, playedMillis = song.playTime) }
            .sortedByDescending { it.playedMillis }

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
        // No screen-specific menu items -- same as Component 4's artist detail screen.
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = false
}
