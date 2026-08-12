package code.name.monkey.retromusic.fragments.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import code.name.monkey.retromusic.model.stats.GenreStat
import kotlin.random.Random

/**
 * ViewModel for the Statistics (main) screen's "Top genres" list (see CLAUDE.md, Component 6
 * -- the pivot away from the pie/bar chart + legend-toggle row to a plain ranked list).
 *
 * Everything here is a mock genre-playtime generator -- there is no repository/DAO backing
 * this yet, since genre and listened-duration aren't tracked anywhere in the schema today
 * (see CLAUDE.md "Schema reality check"). That's Phase B (Component 7). [GenreStat] and the
 * top-N/"Others" grouping logic below are written to survive that swap with minimal changes.
 *
 * Time windows have been removed entirely from every Statistics screen (see CLAUDE.md,
 * Component 6), so this always reflects All Time -- the mock data is generated once and
 * never regenerated.
 */
class StatisticsViewModel : ViewModel() {

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
        regenerateMockStats()
    }

    /** Phase A placeholder data -- see CLAUDE.md "Schema reality check". */
    private fun regenerateMockStats() {
        val random = Random(MOCK_SEED)
        _genreStats.value = MOCK_GENRE_NAMES.mapIndexed { index, name ->
            val baseMinutes = (MOCK_GENRE_NAMES.size - index) * 47 + random.nextInt(0, 30)
            GenreStat(id = index.toLong(), name = name, playedMillis = baseMinutes * 60_000L)
        }
    }

    companion object {
        /** Sentinel id for the synthetic "Others" bucket in [displayGenreStats]. */
        const val OTHERS_ID = -1L

        /** At most this many individual genres are shown before the rest collapse into "Others" -- confirmed at 9, see CLAUDE.md. */
        private const val MAX_DISPLAYED_GENRES = 9

        /** A genre only gets its own row if it has more than this much playtime -- confirmed at 3 hours, see CLAUDE.md. */
        private const val MIN_DISPLAY_MILLIS = 3 * 60 * 60 * 1000L

        private const val MOCK_SEED = 20260806L
        private val MOCK_GENRE_NAMES = listOf(
            "Rock", "Pop", "Hip-Hop", "Electronic", "Jazz",
            "Classical", "R&B", "Metal", "Indie", "Reggae"
        )
    }
}
