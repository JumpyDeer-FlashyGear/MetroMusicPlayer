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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.artist.StatsArtistAdapter
import code.name.monkey.retromusic.databinding.FragmentStatsMediaListBinding
import code.name.monkey.retromusic.db.DailyPlayCountEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.interfaces.IArtistClickListener
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.stats.StatsTimeRange
import code.name.monkey.retromusic.repository.RealRepository
import code.name.monkey.retromusic.util.RetroUtil
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

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
 * Time window was removed entirely (see CLAUDE.md, Component 6), then reintroduced on this
 * screen once real per-day playtime existed to back it -- see CLAUDE.md's Component 7
 * follow-up. [selectedTimeRange] drives [render] via [RealRepository.playTimeInRange];
 * there's no "All Time" choice in the picker, matching the Genre screen's picker.
 *
 * Phase B (Component 7): both the artist list and the playtime used to sort/label it are
 * real now. Per-artist playtime for the selected range is the sum of [Artist.songs]' real
 * daily-rollup playtime, looked up via [RealRepository.playTimeInRange] -- the same source
 * the Statistics screen's Top Genres list uses. One playtime query covers every artist in
 * the list rather than querying per artist.
 */
class StatsArtistsFragment : AbsMainActivityFragment(R.layout.fragment_stats_media_list),
    IArtistClickListener {

    private var _binding: FragmentStatsMediaListBinding? = null
    private val binding get() = _binding!!
    private var latestArtists: List<Artist> = emptyList()
    private lateinit var adapter: StatsArtistAdapter

    private var selectedTimeRange: StatsTimeRange = StatsTimeRange.Week

    // Guards against a slow-loading older range's result overwriting a newer selection's --
    // same reasoning as StatisticsViewModel's genreStatsRequestId.
    private var renderRequestId = 0

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

        setupTimeRangeChips()

        libraryViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            latestArtists = artists
            render()
        }
    }

    // Plain per-chip click listeners rather than ChipGroup's checked-state-change callback --
    // see StatisticsFragment's setupTimeRangeChips for why the Custom chip needs this.
    private fun setupTimeRangeChips() {
        binding.statsTimeRangeChipToday.setOnClickListener { selectTimeRange(StatsTimeRange.Today) }
        binding.statsTimeRangeChipWeek.setOnClickListener { selectTimeRange(StatsTimeRange.Week) }
        binding.statsTimeRangeChipMonth.setOnClickListener { selectTimeRange(StatsTimeRange.Month) }
        binding.statsTimeRangeChipYear.setOnClickListener { selectTimeRange(StatsTimeRange.Year) }
        binding.statsTimeRangeChipCustom.setOnClickListener { showCustomRangePicker() }
    }

    private fun selectTimeRange(range: StatsTimeRange) {
        selectedTimeRange = range
        updateSelectedChip(range)
        render()
    }

    private fun showCustomRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.stats_time_range_custom_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectTimeRange(
                StatsTimeRange.Custom(
                    startEpochDay = DailyPlayCountEntity.epochDayFromUtcMidnightMillis(selection.first),
                    endEpochDay = DailyPlayCountEntity.epochDayFromUtcMidnightMillis(selection.second)
                )
            )
        }
        // Cancelling/dismissing leaves selectedTimeRange unchanged -- explicitly re-sync the
        // chip row, undoing the ChipGroup's own auto-check of the Custom chip.
        picker.addOnNegativeButtonClickListener { updateSelectedChip(selectedTimeRange) }
        picker.addOnCancelListener { updateSelectedChip(selectedTimeRange) }
        picker.show(childFragmentManager, "stats_time_range_custom_picker")
    }

    private fun updateSelectedChip(range: StatsTimeRange) {
        val chipId = when (range) {
            is StatsTimeRange.Today -> binding.statsTimeRangeChipToday.id
            is StatsTimeRange.Week -> binding.statsTimeRangeChipWeek.id
            is StatsTimeRange.Month -> binding.statsTimeRangeChipMonth.id
            is StatsTimeRange.Year -> binding.statsTimeRangeChipYear.id
            is StatsTimeRange.Custom -> binding.statsTimeRangeChipCustom.id
        }
        binding.statsTimeRangeChipGroup.check(chipId)
    }

    private fun render() {
        val requestId = ++renderRequestId
        val range = selectedTimeRange
        lifecycleScope.launch {
            val playtimeByArtistId = withContext(IO) {
                val playTimeBySongId = get<RealRepository>().playTimeInRange(range.startEpochDay, range.endEpochDay)
                latestArtists.associate { artist ->
                    artist.id to artist.songs.sumOf { song -> playTimeBySongId[song.id] ?: 0L }
                }
            }
            if (requestId != renderRequestId) return@launch
            val sorted = latestArtists.sortedByDescending { playtimeByArtistId.getValue(it.id) }
            adapter.playtimeMillisByArtistId = playtimeByArtistId
            adapter.swapDataSet(sorted)
            binding.empty.isVisible = sorted.isEmpty()
        }
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
        // via LibraryViewModel and to key its per-artist/per-album real playtime lookups.
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
