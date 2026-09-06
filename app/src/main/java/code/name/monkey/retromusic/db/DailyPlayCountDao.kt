package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * A song's [DailyPlayCountEntity.playedMillis] summed across a day range.
 * Not a [DailyPlayCountEntity] itself, since once summed across days it no
 * longer corresponds to a single [DailyPlayCountEntity.dayEpoch].
 */
data class SongPlayTimeTotal(
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "total_millis")
    val totalMillis: Long
)

@Dao
interface DailyPlayCountDao {

    // Atomic upsert-increment, not a find-then-add like PlayCountEntity's
    // all-time total: every call here should accumulate onto the existing
    // value for (songId, dayEpoch), never overwrite it.
    @Query(
        """
        INSERT INTO daily_play_count (song_id, day_epoch, played_millis)
        VALUES (:songId, :dayEpoch, :millis)
        ON CONFLICT(song_id, day_epoch) DO UPDATE SET played_millis = played_millis + :millis
        """
    )
    fun addPlayedMillis(songId: Long, dayEpoch: Long, millis: Long)

    @Query(
        """
        SELECT song_id, SUM(played_millis) AS total_millis
        FROM daily_play_count
        WHERE day_epoch BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY song_id
        """
    )
    fun songTotalsBetween(startEpochDay: Long, endEpochDay: Long): List<SongPlayTimeTotal>
}
