package code.name.monkey.retromusic.model.stats

/**
 * A genre's total listening time.
 *
 * Phase A note: instances are produced by
 * [code.name.monkey.retromusic.fragments.statistics.StatisticsViewModel]'s mock generator.
 * Phase B (Component 7) replaces that generator with a real DAO aggregation query over the
 * new listening-event table — this shape is expected to survive that swap largely as-is.
 *
 * [id] is a stable identifier; for the synthetic "Others" bucket it is always
 * [code.name.monkey.retromusic.fragments.statistics.StatisticsViewModel.OTHERS_ID].
 */
data class GenreStat(
    val id: Long,
    val name: String,
    val playedMillis: Long
)
