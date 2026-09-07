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
import code.name.monkey.retromusic.EXTRA_ARTIST_ID
import code.name.monkey.retromusic.EXTRA_ARTIST_NAME
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentStatsArtistDetailBinding
import code.name.monkey.retromusic.db.DailyPlayCountEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.stats.AlbumStat
import code.name.monkey.retromusic.model.stats.SongStat
import code.name.monkey.retromusic.model.stats.StatsTimeRange
import code.name.monkey.retromusic.repository.RealRepository
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.stats.StatsRowBinder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
/**
 * Artist detail stats screen (see CLAUDE.md, Component 4; redesigned in Component 6's pivot
 * away from charts). Opened by tapping an artist in Component 3's `StatsArtistsFragment`.
 *
 * No charts anymore -- a time-range chip row (Today/Week/Month/Year/Custom, same idiom as
 * Component 3's Artists/Albums list screens), then three plain sections, all rendered as
 * ranked text lists via [StatsRowBinder]:
 * - **Overview**: Total Playtime (real, scoped to [selectedTimeRange]), Discography length
 *   (real, library-wide -- sum of every song's actual
 *   [code.name.monkey.retromusic.model.Song.duration] via `artist.songs`, unaffected by the
 *   picker), Albums ([Artist.albumCount]), Songs ([Artist.songCount]) (both library-wide
 *   counts, also unaffected by the picker).
 * - **Top Albums**: every album by this artist ([Artist.albums], uncapped), ranked
 *   descending by playtime within [selectedTimeRange] (summed from that album's songs).
 * - **Top Songs**: every song by this artist ([Artist.songs]), ranked descending by playtime
 *   within [selectedTimeRange], capped at [MAX_TOP_SONGS].
 *
 * Both ranked lists use the same 3-tier sizing as the main screen's Top Genres list (rank 1
 * big, ranks 2-3 medium, rank 4+ small) -- confirmed for every ranked list on these screens,
 * see CLAUDE.md.
 *
 * Phase B (Component 7) + time-range picker follow-up: playtime for the selected range comes
 * from [RealRepository.playTimeInRange], the same daily-rollup source the Statistics screen
 * and the Artists/Albums list screens use -- see those classes' doc comments for the pattern
 * this mirrors (same picker, same request-id guard). Discography length/Albums/Songs stay
 * on the library-wide sources they always used, since those are facts about the library, not
 * about listening activity. The artist itself is real, found by id in [libraryViewModel]'s
 * artist list -- same lookup-by-id pattern Component 3 used for album taps.
 */
class StatsArtistDetailFragment : AbsMainActivityFragment(R.layout.fragment_stats_artist_detail) {

    private var _binding: FragmentStatsArtistDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<StatsArtistDetailFragmentArgs>()
    private var currentArtist: Artist? = null

    private var selectedTimeRange: StatsTimeRange = StatsTimeRange.Week

    // Guards against a slow-loading older range's result overwriting a newer selection's --
    // same reasoning as StatisticsViewModel's genreStatsRequestId.
    private var renderRequestId = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatsArtistDetailBinding.bind(view)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = args.artistName
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        setupTimeRangeChips()

        libraryViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            currentArtist = artists.firstOrNull { it.id == args.artistId }
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
        val artist = currentArtist ?: return
        val requestId = ++renderRequestId
        val range = selectedTimeRange
        lifecycleScope.launch {
            // One query covers every song in the discography for the selected range;
            // Overview and Top Songs read it directly, Top Albums sums it per album.
            val playTimeBySongId = withContext(IO) {
                get<RealRepository>().playTimeInRange(range.startEpochDay, range.endEpochDay)
            }
            if (requestId != renderRequestId) return@launch
            renderOverview(artist, playTimeBySongId)
            renderTopAlbums(artist, playTimeBySongId)
            renderTopSongs(artist, playTimeBySongId)
        }
    }

    private fun renderOverview(artist: Artist, playTimeBySongId: Map<Long, Long>) {
        val totalPlaytimeMillis = artist.songs.sumOf { playTimeBySongId[it.id] ?: 0L }
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

    private fun renderTopAlbums(artist: Artist, playTimeBySongId: Map<Long, Long>) {
        val playTimeByAlbumId = artist.songs.groupBy { it.albumId }
            .mapValues { (_, songs) -> songs.sumOf { playTimeBySongId[it.id] ?: 0L } }
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

    private fun renderTopSongs(artist: Artist, playTimeBySongId: Map<Long, Long>) {
        val stats = artist.songs
            .map { song -> SongStat(id = song.id, title = song.title, playedMillis = playTimeBySongId[song.id] ?: 0L) }
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
        // "View Artist" -- jumps back to the real artist detail screen, the counterpart to
        // the "View Stats" action AbsArtistDetailsFragment's own menu now has. Branches the
        // same way ArtistAdapter's click handler does: libraryViewModel.getArtists() (which
        // this screen's currentArtist was looked up from) returns album-artists when
        // PreferenceUtil.albumArtistsOnly is on, so the destination has to match.
        inflater.inflate(R.menu.menu_stats_artist_detail, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_view_artist) {
            if (PreferenceUtil.albumArtistsOnly) {
                findNavController().navigate(
                    R.id.albumArtistDetailsFragment,
                    bundleOf(EXTRA_ARTIST_NAME to args.artistName)
                )
            } else {
                findNavController().navigate(
                    R.id.artistDetailsFragment,
                    bundleOf(EXTRA_ARTIST_ID to args.artistId)
                )
            }
            return true
        }
        return false
    }

    private companion object {
        /** Top Songs is capped, unlike Top Albums -- confirmed at 15, see CLAUDE.md. */
        const val MAX_TOP_SONGS = 15
    }
}
