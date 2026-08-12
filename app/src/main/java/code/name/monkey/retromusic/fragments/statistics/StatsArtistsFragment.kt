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
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.artist.StatsArtistAdapter
import code.name.monkey.retromusic.databinding.FragmentStatsMediaListBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.interfaces.IArtistClickListener
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.util.RetroUtil
import code.name.monkey.retromusic.util.stats.MockStatsGenerator
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * Artists sorted by total listened time (see CLAUDE.md, Component 3).
 *
 * Visually this is [code.name.monkey.retromusic.fragments.artists.ArtistsFragment]'s item
 * grid reused as-is via [StatsArtistAdapter] (same layout/adapter, subtitle swapped for a
 * formatted playtime) — deliberately *not* built on top of
 * [code.name.monkey.retromusic.fragments.base.AbsRecyclerViewCustomGridSizeFragment] like
 * that screen is, since this is a standalone pushed screen (own toolbar, own back button)
 * rather than one of the bottom-nav library tabs, so it doesn't need that base class's
 * sort-order/grid-size options menu — nothing in Component 3's brief asked for one.
 *
 * Time window has been removed entirely (see CLAUDE.md, Component 6) -- this screen always
 * shows All-Time playtime now, so there's no per-screen time-window ViewModel or
 * time-window button here anymore (the old `StatsTimeWindowViewModel` is deleted).
 *
 * Phase A note: like every Statistics screen, the ranking itself is mocked (see
 * [code.name.monkey.retromusic.util.stats.MockStatsGenerator]) — the artist list is real
 * (from `LibraryViewModel`), only the playtime used to sort/label it isn't, since nothing
 * tracks real listened duration yet (CLAUDE.md "Schema reality check").
 */
class StatsArtistsFragment : AbsMainActivityFragment(R.layout.fragment_stats_media_list),
    IArtistClickListener {

    private var _binding: FragmentStatsMediaListBinding? = null
    private val binding get() = _binding!!
    private var latestArtists: List<Artist> = emptyList()
    private lateinit var adapter: StatsArtistAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatsMediaListBinding.bind(view)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitle(R.string.artists)
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())
        binding.emptyText.setText(R.string.no_artists)

        adapter = StatsArtistAdapter(requireActivity(), emptyList(), R.layout.item_grid_circle, this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), gridCount())

        libraryViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            latestArtists = artists
            render()
        }
    }

    private fun render() {
        val playtimeByArtistId = latestArtists.associate { artist ->
            artist.id to MockStatsGenerator.playedMillisFor(artist.id)
        }
        val sorted = latestArtists.sortedByDescending { playtimeByArtistId.getValue(it.id) }
        adapter.playtimeMillisByArtistId = playtimeByArtistId
        adapter.swapDataSet(sorted)
        binding.empty.isVisible = sorted.isEmpty()
    }

    private fun gridCount(): Int {
        if (RetroUtil.isTablet) {
            return if (RetroUtil.isLandscape) 6 else 4
        }
        return if (RetroUtil.isLandscape) 4 else 2
    }

    override fun onArtist(artistId: Long, view: View) {
        val artist = latestArtists.firstOrNull { it.id == artistId } ?: return
        // Component 4 (artist detail stats) -- see CLAUDE.md. Passes artistId (not just the
        // name) since StatsArtistDetailFragment needs the real id to look the artist back up
        // via LibraryViewModel and to key MockStatsGenerator's per-artist/per-album mock data.
        findNavController().navigate(
            R.id.statsArtistDetailFragment,
            bundleOf("artistId" to artist.id, "artistName" to artist.name)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        // No screen-specific menu items -- see class doc comment on why this isn't built on
        // AbsRecyclerViewCustomGridSizeFragment's sort-order/grid-size menu.
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = false
}
