package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.util.TimeZone

/**
 * One row per song per calendar day, holding the total milliseconds actually
 * listened to that song on that day. Fed from the same pause-aware elapsed
 * time [SongPlayCountHelper] already measures for [PlayCountEntity.playTime]
 * (see [code.name.monkey.retromusic.service.MusicService.saveSongPlayTime]).
 *
 * This is a bounded, pre-aggregated daily rollup — not a raw per-play event
 * log — so it grows by at most (unique songs played that day) rows per day,
 * no matter how many times a song is replayed within it.
 */
@Entity(
    tableName = "daily_play_count",
    primaryKeys = ["song_id", "day_epoch"],
    // Must mirror MIGRATION_25_26's manually-created index exactly (same
    // default name Room derives from this declaration: index_<table>_<col>,
    // i.e. index_daily_play_count_day_epoch) - Room's post-migration schema
    // validation compares the real DB against this annotation, and any
    // index present in one but not the other fails validation, crashing
    // with "Migration didn't properly handle: daily_play_count" at startup.
    indices = [Index(value = ["day_epoch"])]
)
data class DailyPlayCountEntity(
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "day_epoch")
    val dayEpoch: Long,
    @ColumnInfo(name = "played_millis")
    val playedMillis: Long
) {
    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Local-calendar-day index, one integer per day, monotonically
         * increasing — the same role `java.time.LocalDate.toEpochDay()`
         * would play. Computed by hand rather than with java.time since this
         * app's minSdk is 21 and has no core-library-desugaring dependency,
         * so java.time.* is not safely usable here.
         */
        fun todayEpochDay(): Long {
            val now = System.currentTimeMillis()
            val offsetMillis = TimeZone.getDefault().getOffset(now)
            return (now + offsetMillis) / DAY_MILLIS
        }
    }
}
