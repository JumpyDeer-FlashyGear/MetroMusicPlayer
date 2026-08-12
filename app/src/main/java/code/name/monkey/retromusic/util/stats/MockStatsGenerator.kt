package code.name.monkey.retromusic.util.stats

import kotlin.random.Random

/**
 * Deterministic per-id mock listening-time generator for the Statistics screens (see
 * CLAUDE.md, Component 3, and the Component 6 pivot from pie/bar charts to plain ranked
 * lists). There is no real per-artist/per-album/per-song playtime yet -- see CLAUDE.md
 * "Schema reality check" -- so this produces a plausible-looking, stable total per id
 * instead.
 *
 * Deterministic per [id] alone. Time windows have been removed entirely from every
 * Statistics screen (see CLAUDE.md, Component 6), so every caller now always wants the same
 * All-Time figure for a given id -- re-observing the same LiveData emission twice, or
 * recomposing after a configuration change, doesn't jitter the sort order or the displayed
 * numbers. Phase B (Component 7) replaces every call site of this with a real DAO
 * aggregation query.
 */
object MockStatsGenerator {

    private const val SEED = 20260806L
    private const val MIN_BASE_MINUTES = 5
    private const val BASE_MINUTES_RANGE = 420

    fun playedMillisFor(id: Long): Long {
        val random = Random(id * 1_000_003L + SEED)
        val baseMinutes = MIN_BASE_MINUTES + random.nextInt(BASE_MINUTES_RANGE)
        return baseMinutes * 60_000L
    }
}
