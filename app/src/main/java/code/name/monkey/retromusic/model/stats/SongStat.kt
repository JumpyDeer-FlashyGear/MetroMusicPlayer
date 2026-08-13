package code.name.monkey.retromusic.model.stats

/**
 * A song's listening time, used by the Statistics Artist/Album detail screens' Top Songs
 * lists (see CLAUDE.md, Component 6 -- the pivot away from pie/bar charts to plain ranked
 * lists).
 *
 * Phase B (Component 7): [playedMillis] is the real per-song `PlayCountEntity.playTime`,
 * the same column the "most played" screen and the Genre/Artist/Album detail screens
 * already read -- see CLAUDE.md's Component 7 update.
 */
data class SongStat(
    val id: Long,
    val title: String,
    val playedMillis: Long
)
