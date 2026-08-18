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
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.EXTRA_ALBUM_ID
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentStatsAlbumDetailBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.albumCoverOptions
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.model.stats.SongStat
import code.name.monkey.retromusic.repository.RealRepository
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.stats.StatsRowBinder
import com.bumptech.glide.Glide
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
            val album = albums.firstOrNull { it.id == args.albumId }
            currentAlbum = album
            if (album != null) {
                loadAlbumCover(album)
            }
            render()
        }
    }

    private fun loadAlbumCover(album: Album) {
        Glide.with(requireContext())
            .load(RetroGlideExtension.getSongModel(album.safeGetFirstSong()))
            .albumCoverOptions(album.safeGetFirstSong())
            .into(binding.albumCoverImage)
    }

    private fun render() {
        val album = currentAlbum ?: return
        lifecycleScope.launch {
            val songsWithPlayTime = withContext(IO) {
                get<RealRepository>().songsWithPlayTime(album.songs)
            }
            val genreName = withContext(IO) { resolveAlbumGenre(album) }
            renderOverview(album, songsWithPlayTime, genreName)
            renderTopSongs(songsWithPlayTime)
        }
    }

    /**
     * Genre membership isn't stored in Room (see CLAUDE.md, "Schema reality check") -- the
     * only way to resolve a song's genre is the live [RealRepository.fetchGenres] /
     * [RealRepository.getGenre] pair [StatisticsViewModel] already uses for the main Top
     * Genres list. There's no per-song reverse lookup, so this walks every genre's song list
     * looking for one of this album's song ids. If the album's songs span more than one
     * genre (uncommon, but MediaStore doesn't enforce a single genre per album), the first
     * match wins -- same one-genre-per-album assumption the rest of this screen makes.
     */
    private suspend fun resolveAlbumGenre(album: Album): String? {
        val albumSongIds = album.songs.map { it.id }.toSet()
        val realRepository = get<RealRepository>()
        val genres = realRepository.fetchGenres()
        for (genre in genres) {
            val genreSongIds = realRepository.getGenre(genre.id).map { it.id }.toSet()
            if (genreSongIds.any { it in albumSongIds }) {
                return genre.name
            }
        }
        return null
    }

    private fun renderOverview(album: Album, songsWithPlayTime: List<Song>, genreName: String?) {
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
        StatsRowBinder.addOverviewRow(
            inflater, container, getString(R.string.genre), genreName ?: getString(R.string.stats_unknown_genre)
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
        // "View Album" -- jumps back to the real AlbumDetailsFragment for this album, the
        // counterpart to the "View Stats" action AlbumDetailsFragment's own menu now has.
        inflater.inflate(R.menu.menu_stats_album_detail, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_view_album) {
            findNavController().navigate(
                R.id.albumDetailsFragment,
                bundleOf(EXTRA_ALBUM_ID to args.albumId)
            )
            return true
        }
        return false
    }
}
