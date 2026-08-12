package code.name.monkey.retromusic.model.stats

/**
 * An album's listening time, scoped to one artist (see CLAUDE.md, Component 4 -- the Artist
 * Detail screen's Top Albums list).
 *
 * Phase A note: instances are produced by
 * [code.name.monkey.retromusic.util.stats.MockStatsGenerator.playedMillisFor], the exact
 * same function Component 3's Albums list uses — there's no separate "by artist" mock
 * generator, since the mock is keyed by album id regardless of which screen is asking.
 * Phase B (Component 7) replaces this with a real DAO aggregation query scoped to artistId.
 */
data class AlbumStat(
    val id: Long,
    val name: String,
    val playedMillis: Long
)
