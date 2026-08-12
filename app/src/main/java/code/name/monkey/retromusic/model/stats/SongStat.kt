package code.name.monkey.retromusic.model.stats

/**
 * A song's mock listening time, used by the Statistics Artist/Album detail screens' Top
 * Songs lists (see CLAUDE.md, Component 6 -- the pivot away from pie/bar charts to plain
 * ranked lists).
 *
 * Phase A note: instances are produced by
 * [code.name.monkey.retromusic.util.stats.MockStatsGenerator.playedMillisFor] keyed on the
 * real song id -- there's no real per-song listened duration yet (see CLAUDE.md "Schema
 * reality check"). Phase B (Component 7) replaces this with a real DAO aggregation query.
 */
data class SongStat(
    val id: Long,
    val title: String,
    val playedMillis: Long
)
