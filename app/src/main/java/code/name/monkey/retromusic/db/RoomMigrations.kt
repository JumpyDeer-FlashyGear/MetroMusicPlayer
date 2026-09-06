package code.name.monkey.retromusic.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE PlayCountEntity ADD COLUMN play_time INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_play_count (
                song_id INTEGER NOT NULL,
                day_epoch INTEGER NOT NULL,
                played_millis INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(song_id, day_epoch)
            )
            """
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_daily_play_count_day_epoch ON daily_play_count(day_epoch)"
        )
    }
}
