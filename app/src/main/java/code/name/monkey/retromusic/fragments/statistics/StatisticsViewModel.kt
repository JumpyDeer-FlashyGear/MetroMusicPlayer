package code.name.monkey.retromusic.fragments.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.model.stats.GenreStat
import code.name.monkey.retromusic.repository.RealRepository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

/**
 * ViewModel for the Statistics (main) screen's "Top genres" list (see CLAUDE.md, Component 6
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
     * 3-hour floor or just past the top-9 cap -- is folded into a single "Others" entry,
     * which is only appended if there's anything left to fold in.
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

    /** Grand total across every genre (not just the ones [displayGenreStats] shows), for the header. */
    val totalPlaytimeMillis: LiveData<Long> = _genreStats.map { stats -> stats.sumOf { it.playedMillis } }

    init {
        loadGenreStats()
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

    companion object {
        /** Sentinel id for the synthetic "Others" bucket in [displayGenreStats]. */
        const val OTHERS_ID = -1L

        /** At most this many individual genres are shown before the rest collapse into "Others" -- confirmed at 9, see CLAUDE.md. */
        private const val MAX_DISPLAYED_GENRES = 9

        /** A genre only gets its own row if it has more than this much playtime -- confirmed at 3 hours, see CLAUDE.md. */
        private const val MIN_DISPLAY_MILLIS = 3 * 60 * 60 * 1000L
    }
}
