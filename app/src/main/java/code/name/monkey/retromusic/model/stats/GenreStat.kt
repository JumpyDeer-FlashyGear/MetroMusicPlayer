package code.name.monkey.retromusic.model.stats

/**
 * A genre's total listening time.
 *
 * Phase B (Component 7): produced by
 * [code.name.monkey.retromusic.fragments.statistics.StatisticsViewModel] from real per-song
 * playtime (`PlayCountEntity.playTime`), summed over each genre's songs (resolved live via
 * [code.name.monkey.retromusic.repository.RealRepository.getGenre], the same lookup the
 * Genre Detail screen uses) -- see CLAUDE.md's Component 7 update.
 *
 * [id] is a stable identifier; for the synthetic "Others" bucket it is always
 * [code.name.monkey.retromusic.fragments.statistics.StatisticsViewModel.OTHERS_ID].
 */
data class GenreStat(
    val id: Long,
    val name: String,
    val playedMillis: Long
)
