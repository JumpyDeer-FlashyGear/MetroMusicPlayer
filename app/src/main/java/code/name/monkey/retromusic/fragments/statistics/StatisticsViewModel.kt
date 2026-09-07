package code.name.monkey.retromusic.fragments.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.model.stats.GenreStat
import code.name.monkey.retromusic.model.stats.LibraryOverviewStat
import code.name.monkey.retromusic.model.stats.StatsTimeRange
import code.name.monkey.retromusic.repository.RealRepository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

/**
 * ViewModel for the Statistics (main) screen: the library-overview block (Total Playtime,
 * Songs, Albums, Artists) plus the "Top genres" list below it (see CLAUDE.md, Component 6
 * -- the pivot away from the pie/bar chart + legend-toggle row to a plain ranked list).
 *
 * Phase B (Component 7): genre membership is resolved the exact same way
 * [code.name.monkey.retromusic.fragments.genres.GenreDetailsViewModel] resolves it for the
 * Genre Detail screen -- [RealRepository.fetchGenres] for the genre list,
 * [RealRepository.getGenre] per genre for that genre's songs (a live MediaStore query, since
 * genre membership isn't stored in Room -- see CLAUDE.md "Schema reality check"). Per-genre
 * playtime for the selected range comes from [RealRepository.playTimeInRange], the daily
 * rollup table fed by the same playback hook (`SongPlayCountHelper`/
 * `MusicService.saveSongPlayTime`) that already keeps `PlayCountEntity.playTime`'s all-time
 * total up to date -- see CLAUDE.md's Component 7 follow-up. [overviewStats]' Total Playtime
 * still reads that all-time total directly via [RealRepository.playCountSongs], unaffected by
 * [selectedTimeRange].
 *
 * Time windows were removed entirely from every Statistics screen by Component 6, then
 * reintroduced on this screen only once real per-day playtime existed to back it (see
 * CLAUDE.md, Component 7 follow-up): [selectedTimeRange] drives [displayGenreStats] via
 * [RealRepository.playTimeInRange], except [StatsTimeRange.AllTime] which reads
 * [RealRepository.playCountSongs] directly instead (the true all-time total, same source
 * [overviewStats]' Total Playtime already used).
 */
class StatisticsViewModel(private val realRepository: RealRepository) : ViewModel() {

    private val _genreStats = MutableLiveData<List<GenreStat>>()

    /**
     * [_genreStats], filtered to genres with more than [MIN_DISPLAY_MILLIS] of playtime,
     * sorted descending by playtime, and capped at [MAX_DISPLAYED_GENRES]. The floor is
     * deliberately tiny (1 second, not the old 2-hour minimum) -- just enough to keep a
     * genre with literally zero/negligible playtime from getting its own row, without
     * hiding anything a person could actually call "listened to". Every genre that doesn't
     * individually qualify for its own row -- whether because it falls under that floor or
     * just past the top-9 cap -- is folded into a single "Others" entry, which is only
     * appended if there's anything left to fold in. At most 9 genres get their own row plus
     * one "Others" row (10 total) -- see [MAX_DISPLAYED_GENRES]/[MIN_DISPLAY_MILLIS].
     */
    val displayGenreStats: LiveData<List<GenreStat>> = _genreStats.map { stats ->
        val sorted = stats.sortedByDescending { it.playedMillis }
        val shown = sorted.filter { it.playedMillis > MIN_DISPLAY_MILLIS }.take(MAX_DISPLAYED_GENRES)
        val shownIds = shown.map { it.id }.toSet()
        val othersMillis = stats.filter { it.id !in shownIds }.sumOf { it.playedMillis }
        if (othersMillis > 0) {
            shown + GenreStat(id = OTHERS_ID, name = "", playedMillis = othersMillis)
        } else {
            shown
        }
    }

    private val _overviewStats = MutableLiveData<LibraryOverviewStat>()

    /**
     * Library-wide totals for the overview block at the top of the screen (Total Playtime,
     * Songs, Albums, Artists) -- see CLAUDE.md. This replaced the old header row that paired
     * "Top genres" with the grand total playtime; that total now lives here instead. Always
     * All Time, unaffected by [selectedTimeRange].
     */
    val overviewStats: LiveData<LibraryOverviewStat> = _overviewStats

    private val _selectedTimeRange = MutableLiveData<StatsTimeRange>(DEFAULT_TIME_RANGE)

    /** Drives [displayGenreStats]; the Statistics screen syncs its time-picker chips to this. */
    val selectedTimeRange: LiveData<StatsTimeRange> = _selectedTimeRange

    // Guards against a slow-loading older range's result overwriting a newer selection's --
    // e.g. tapping Month then quickly tapping Week shouldn't let Month's result land second.
    private var genreStatsRequestId = 0

    init {
        loadGenreStats(DEFAULT_TIME_RANGE)
        loadOverviewStats()
    }

    fun selectTimeRange(range: StatsTimeRange) {
        _selectedTimeRange.value = range
        loadGenreStats(range)
    }

    private fun loadGenreStats(range: StatsTimeRange) {
        val requestId = ++genreStatsRequestId
        viewModelScope.launch(IO) {
            val genres = realRepository.fetchGenres()
            val playTimeBySongId = if (range is StatsTimeRange.AllTime) {
                realRepository.playCountSongs().associate { it.id to it.playTime }
            } else {
                realRepository.playTimeInRange(range.startEpochDay, range.endEpochDay)
            }
            val stats = genres.map { genre ->
                val playedMillis = realRepository.getGenre(genre.id)
                    .sumOf { song -> playTimeBySongId[song.id] ?: 0L }
                GenreStat(id = genre.id, name = genre.name, playedMillis = playedMillis)
            }
            if (requestId == genreStatsRequestId) {
                _genreStats.postValue(stats)
            }
        }
    }

    /**
     * Total playtime is summed directly from every song's `PlayCountEntity.playTime` rather
     * than derived from [displayGenreStats]/the per-genre breakdown, since a song can in
     * principle belong to more than one MediaStore genre and would otherwise be
     * double-counted here. Songs/Albums use the library's real totals; Artists deliberately
     * counts album artists ([RealRepository.albumArtists]) rather than plain per-track
     * artists, so songs with multiple featured artists don't fragment into extra entries --
     * see [LibraryOverviewStat]'s doc comment.
     */
    private fun loadOverviewStats() = viewModelScope.launch(IO) {
        val totalPlaytimeMillis = realRepository.playCountSongs().sumOf { it.playTime }
        val songCount = realRepository.allSongs().size
        val albumCount = realRepository.fetchAlbums().size
        val artistCount = realRepository.albumArtists().size
        _overviewStats.postValue(
            LibraryOverviewStat(
                totalPlaytimeMillis = totalPlaytimeMillis,
                songCount = songCount,
                albumCount = albumCount,
                artistCount = artistCount
            )
        )
    }

    companion object {
        /** Sentinel id for the synthetic "Others" bucket in [displayGenreStats]. */
        const val OTHERS_ID = -1L

        /**
         * Picker default on first opening the screen. Reset to All Time on request -- it's
         * both the most immediately familiar figure (it's what this screen always showed
         * before the picker existed) and avoids an initially-empty-looking list for
         * lighter/newer libraries where Today/Week/Month might show little to nothing yet.
         */
        private val DEFAULT_TIME_RANGE: StatsTimeRange = StatsTimeRange.AllTime

        /** At most this many individual genres are shown before the rest collapse into "Others" -- confirmed at 9, see CLAUDE.md. */
        private const val MAX_DISPLAYED_GENRES = 9

        /** A genre only gets its own row if it has more than this much playtime -- set to 1 second, down from the old 2-hour floor, per follow-up request. */
        private const val MIN_DISPLAY_MILLIS = 1000L
    }
}
