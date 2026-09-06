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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentStatisticsBinding
import code.name.monkey.retromusic.db.DailyPlayCountEntity
import code.name.monkey.retromusic.extensions.applyToolbar
import code.name.monkey.retromusic.model.stats.StatsTimeRange
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.stats.StatsRowBinder
import com.google.android.material.datepicker.MaterialDatePicker
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Statistics screen (see CLAUDE.md, Component 2; redesigned in Component 6's pivot away
 * from charts). No genre legend row, no pie/bar chart toggle anymore -- just a
 * library-overview block (Total Playtime, Songs, Albums, Artists), the Artists/Albums
 * entry-point buttons, a small time-range chip row (Today/Week/Month/Year/Custom), and a
 * ranked "Top genres" list below that scoped to whichever range is selected, all against
 * [StatisticsViewModel]. The time-range chips were reintroduced on top of Component 6's
 * plain-list redesign once real per-day playtime existed to back them -- see CLAUDE.md's
 * Component 7 follow-up.
 */
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyToolbar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupComponent3Buttons()
        setupTimeRangeChips()
        observeViewModel()

    }

    private fun setupComponent3Buttons() {
        binding.statsArtistsButton.setOnClickListener {
            findNavController().navigate(R.id.statsArtistsFragment)
        }
        binding.statsAlbumsButton.setOnClickListener {
            findNavController().navigate(R.id.statsAlbumsFragment)
        }
    }

    // Plain per-chip click listeners rather than ChipGroup's checked-state-change callback:
    // the Custom chip must always reopen the date-range picker when tapped, even when it's
    // already the checked chip (e.g. to pick a different range) -- a checked-state listener
    // wouldn't fire in that case, since tapping an already-checked chip in a singleSelection
    // group doesn't change what's checked.
    private fun setupTimeRangeChips() {
        binding.statsTimeRangeChipToday.setOnClickListener {
            viewModel.selectTimeRange(StatsTimeRange.Today)
        }
        binding.statsTimeRangeChipWeek.setOnClickListener {
            viewModel.selectTimeRange(StatsTimeRange.Week)
        }
        binding.statsTimeRangeChipMonth.setOnClickListener {
            viewModel.selectTimeRange(StatsTimeRange.Month)
        }
        binding.statsTimeRangeChipYear.setOnClickListener {
            viewModel.selectTimeRange(StatsTimeRange.Year)
        }
        binding.statsTimeRangeChipCustom.setOnClickListener {
            showCustomRangePicker()
        }
    }

    private fun showCustomRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.stats_time_range_custom_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            viewModel.selectTimeRange(
                StatsTimeRange.Custom(
                    startEpochDay = DailyPlayCountEntity.epochDayFromUtcMidnightMillis(selection.first),
                    endEpochDay = DailyPlayCountEntity.epochDayFromUtcMidnightMillis(selection.second)
                )
            )
        }
        // Cancelling/dismissing leaves the ViewModel's range unchanged, so LiveData won't
        // re-emit on its own -- explicitly re-sync the chip row back to whatever's still
        // actually selected, undoing the ChipGroup's own auto-check of the Custom chip.
        picker.addOnNegativeButtonClickListener { syncSelectedChip() }
        picker.addOnCancelListener { syncSelectedChip() }
        picker.show(childFragmentManager, "stats_time_range_custom_picker")
    }

    private fun syncSelectedChip() {
        viewModel.selectedTimeRange.value?.let { updateSelectedChip(it) }
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

    private fun observeViewModel() {
        viewModel.selectedTimeRange.observe(viewLifecycleOwner) { range ->
            updateSelectedChip(range)
        }
        viewModel.overviewStats.observe(viewLifecycleOwner) { overview ->
            binding.overviewTotalPlaytimeText.text =
                MusicUtil.getReadableDurationString(overview.totalPlaytimeMillis)
            binding.overviewSongsText.text = overview.songCount.toString()
            binding.overviewAlbumsText.text = overview.albumCount.toString()
            binding.overviewArtistsText.text = overview.artistCount.toString()
        }
        viewModel.displayGenreStats.observe(viewLifecycleOwner) { stats ->
            binding.genreListContainer.removeAllViews()
            stats.forEachIndexed { index, stat ->
                val name = if (stat.id == StatisticsViewModel.OTHERS_ID) {
                    getString(R.string.others)
                } else {
                    stat.name
                }
                StatsRowBinder.addRankRow(
                    layoutInflater, binding.genreListContainer, index + 1, name, stat.playedMillis
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
