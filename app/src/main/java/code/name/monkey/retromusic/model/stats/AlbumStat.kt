package code.name.monkey.retromusic.model.stats

/**
 * An album's listening time, scoped to one artist (see CLAUDE.md, Component 4 -- the Artist
 * Detail screen's Top Albums list).
 *
 * Phase B (Component 7): produced by
 * [code.name.monkey.retromusic.fragments.statistics.StatsArtistDetailFragment] from real
 * per-song playtime (`PlayCountEntity.playTime`), summed over each album's songs -- see
 * CLAUDE.md's Component 7 update.
 */
data class AlbumStat(
    val id: Long,
    val name: String,
    val playedMillis: Long
)
