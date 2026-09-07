package code.name.monkey.retromusic.model.stats

import code.name.monkey.retromusic.db.DailyPlayCountEntity

/**
 * A selectable range for the Statistics screen's time picker (see CLAUDE.md,
 * Component 7 follow-up: real per-day playtime reopened time-window
 * filtering that Component 6 had removed entirely). [startEpochDay] and
 * [endEpochDay] are inclusive day-epoch bounds
 * ([DailyPlayCountEntity.todayEpochDay]-compatible), fed straight into
 * [code.name.monkey.retromusic.repository.RealRepository.playTimeInRange]
 * -- except [AllTime], which every call site special-cases to query
 * [code.name.monkey.retromusic.repository.RealRepository.playCountSongs]
 * instead (the true all-time `PlayCountEntity.playTime` total, correct
 * regardless of how far back `daily_play_count`'s rollup actually goes).
 * [AllTime]'s own [startEpochDay]/[endEpochDay] are just a defensive wide
 * fallback for that special-casing, not meant to be queried directly.
 */
sealed class StatsTimeRange {
    abstract val startEpochDay: Long
    abstract val endEpochDay: Long

    object Today : StatsTimeRange() {
        override val endEpochDay get() = DailyPlayCountEntity.todayEpochDay()
        override val startEpochDay get() = endEpochDay
    }

    object Week : StatsTimeRange() {
        override val endEpochDay get() = DailyPlayCountEntity.todayEpochDay()
        override val startEpochDay get() = endEpochDay - 6
    }

    object Month : StatsTimeRange() {
        override val endEpochDay get() = DailyPlayCountEntity.todayEpochDay()
        override val startEpochDay get() = endEpochDay - 29
    }

    object AllTime : StatsTimeRange() {
        override val endEpochDay get() = DailyPlayCountEntity.todayEpochDay()
        override val startEpochDay get() = 0L
    }

    data class Custom(
        override val startEpochDay: Long,
        override val endEpochDay: Long
    ) : StatsTimeRange()
}
