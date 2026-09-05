package code.name.monkey.retromusic.fragments.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.model.stats.GenreStat
import code.name.monkey.retromusic.model.stats.LibraryOverviewStat
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
 * genre membership isn't stored in Room -- see CLAUDE.md "Schema reality check"). Playtime is
 * resolved the same way the Genre/Artist/Album detail screens and the "most played" screen
 * already resolve it: [RealRepository.playCountSongs], the real per-song
 * `PlayCountEntity.playTime` column that the playback service
 * (`SongPlayCountHelper`/`MusicService.saveSongPlayTime`) already keeps up to date. No new
 * schema, DAO, or playback hook was needed for this -- it already existed in this repo (see
 * CLAUDE.md's Component 7 update).
 *
 * Time windows have been removed entirely from every Statistics screen (see CLAUDE.md,
 * Component 6), so this always reflects All Time.
 */
class StatisticsViewModel(private val realRepository: RealRepository) : ViewModel() {

    private val _genreStats = MutableLiveData<List<GenreStat>>()

    /**
     * [_genreStats], filtered to genres with more than [MIN_DISPLAY_MILLIS] of playtime,
     * sorted descending by playtime, and capped at [MAX_DISPLAYED_GENRES]. Every genre that
     * doesn't individually qualify for its own row -- whether because it falls under the
     * 2-hour floor or just past the top-9 cap -- is folded into a single "Others" entry,
     * which is only appended if there's anything left to fold in. At most 9 genres get their
     * own row plus one "Others" row (10 total) -- see [MAX_DISPLAYED_GENRES]/[MIN_DISPLAY_MILLIS].
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
     * "Top genres" with the grand total playtime; that total now lives here instead.
     */
    val overviewStats: LiveData<LibraryOverviewStat> = _overviewStats

    init {
        loadGenreStats()
        loadOverviewStats()
    }

    private fun loadGenreStats() = viewModelScope.launch(IO) {
        val genres = realRepository.fetchGenres()
        val playTimeBySongId = realRepository.playCountSongs().associate { it.id to it.playTime }
        val stats = genres.map { genre ->
            val playedMillis = realRepository.getGenre(genre.id)
                .sumOf { song -> playTimeBySongId[song.id] ?: 0L }
            GenreStat(id = genre.id, name = genre.name, playedMillis = playedMillis)
        }
        _genreStats.postValue(stats)
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

        /** At most this many individual genres are shown before the rest collapse into "Others" -- confirmed at 9, see CLAUDE.md. */
        private const val MAX_DISPLAYED_GENRES = 9

        /** A genre only gets its own row if it has more than this much playtime -- lowered from 3 hours to 2 hours per follow-up request. */
        private const val MIN_DISPLAY_MILLIS = 2 * 60 * 60 * 1000L
    }
}
