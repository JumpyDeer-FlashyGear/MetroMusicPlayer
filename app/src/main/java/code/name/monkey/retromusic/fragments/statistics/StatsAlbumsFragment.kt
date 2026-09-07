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
import code.name.monkey.retromusic.adapter.album.StatsAlbumAdapter
import code.name.monkey.retromusic.databinding.FragmentStatsMediaListBinding
import code.name.monkey.retromusic.db.DailyPlayCountEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.interfaces.IAlbumClickListener
import code.name.monkey.retromusic.model.Album
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
 * Albums sorted by total listened time (see CLAUDE.md, Component 3). Mirrors
 * [StatsArtistsFragment] exactly — see that class's doc comment for the reasoning behind
 * this screen's structure (reused item grid/adapter, no sort/grid-size menu, mocked
 * ranking). The two aren't merged into one generic fragment because the rest of this
 * codebase consistently keeps Artists/Albums as separate parallel classes (`ArtistAdapter`/
 * `AlbumAdapter`, `ArtistsFragment`/`AlbumsFragment`, etc.) rather than genericizing over
 * the item type, so this follows the same convention.
 *
 * Time window was removed entirely (see CLAUDE.md, Component 6), then reintroduced on this
 * screen once real per-day playtime existed to back it -- see [StatsArtistsFragment]'s doc
 * comment for the pattern this mirrors (same picker, same request-id guard, same reasoning).
 *
 * Phase B (Component 7): both the album list and the playtime used to sort/label it are
 * real now -- see [StatsArtistsFragment]'s doc comment for the pattern this mirrors
 * (per-album playtime for the selected range summed from [Album.songs]' real daily-rollup
 * playtime via [RealRepository.playTimeInRange], one playtime query for the whole list).
 */
class StatsAlbumsFragment : AbsMainActivityFragment(R.layout.fragment_stats_media_list),
    IAlbumClickListener {

    private var _binding: FragmentStatsMediaListBinding? = null
    private val binding get() = _binding!!
    private var latestAlbums: List<Album> = emptyList()
    private lateinit var adapter: StatsAlbumAdapter

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
        binding.toolbar.setTitle(R.string.albums)
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())
        binding.emptyText.setText(R.string.no_albums)

        adapter = StatsAlbumAdapter(requireActivity(), emptyList(), R.layout.item_grid, this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), gridCount())

        setupTimeRangeChips()

        libraryViewModel.getAlbums().observe(viewLifecycleOwner) { albums ->
            latestAlbums = albums
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
            val playtimeByAlbumId = withContext(IO) {
                val playTimeBySongId = get<RealRepository>().playTimeInRange(range.startEpochDay, range.endEpochDay)
                latestAlbums.associate { album ->
                    album.id to album.songs.sumOf { song -> playTimeBySongId[song.id] ?: 0L }
                }
            }
            if (requestId != renderRequestId) return@launch
            val sorted = latestAlbums.sortedByDescending { playtimeByAlbumId.getValue(it.id) }
            adapter.playtimeMillisByAlbumId = playtimeByAlbumId
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

    override fun onAlbumClick(albumId: Long, view: View) {
        val album = latestAlbums.firstOrNull { it.id == albumId } ?: return
        // Component 5 (album detail stats) -- see CLAUDE.md. Passes albumId (not just the
        // name) since StatsAlbumDetailFragment needs the real id to look the album back up
        // via LibraryViewModel and to key its per-album real playtime lookup.
        findNavController().navigate(
            R.id.statsAlbumDetailFragment,
            bundleOf("albumId" to album.id, "albumName" to album.title)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        // No screen-specific menu items -- see class doc comment.
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = false
}
