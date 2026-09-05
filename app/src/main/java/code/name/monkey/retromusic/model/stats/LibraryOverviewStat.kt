package code.name.monkey.retromusic.model.stats

/**
 * Library-wide totals shown in the main Statistics screen's overview block, above the
 * Artists/Albums buttons and the Top Genres list (see CLAUDE.md -- the overview block that
 * replaced the old header-row "Top genres" / grand-total-playtime pairing).
 *
 * [totalPlaytimeMillis] is the real total across every song's `PlayCountEntity.playTime`
 * (the same column the Genre/Artist/Album detail screens and the "most played" screen already
 * read -- see CLAUDE.md's Component 7 update), summed directly rather than derived from the
 * per-genre breakdown, since a song can in principle belong to more than one MediaStore genre
 * and would otherwise be double-counted.
 *
 * [songCount] and [albumCount] are the library's total song/album counts
 * ([code.name.monkey.retromusic.repository.RealRepository.allSongs] /
 * `fetchAlbums`). [artistCount] deliberately counts **album artists**
 * ([code.name.monkey.retromusic.repository.RealRepository.albumArtists]) rather than the
 * plain per-track artist list, since a song with multiple featured artists would otherwise
 * fragment into several distinct "artist" entries for what is really one album artist.
 */
data class LibraryOverviewStat(
    val totalPlaytimeMillis: Long,
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int
)
