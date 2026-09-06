package code.name.monkey.retromusic.model.stats

import code.name.monkey.retromusic.db.DailyPlayCountEntity

/**
 * A selectable range for the Statistics screen's time picker (see CLAUDE.md,
 * Component 7 follow-up: real per-day playtime reopened time-window
 * filtering that Component 6 had removed entirely). [startEpochDay] and
 * [endEpochDay] are inclusive day-epoch bounds
 * ([DailyPlayCountEntity.todayEpochDay]-compatible), fed straight into
 * [code.name.monkey.retromusic.repository.RealRepository.playTimeInRange].
 *
 * There is deliberately no "All Time" option here — that figure lives in
 * the Overview block instead (`PlayCountEntity.playTime`, unaffected by
 * this picker); this type only covers the specific presets asked for
 * (Today/Week/Month/Year) plus a user-chosen Custom range.
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

    object Year : StatsTimeRange() {
        override val endEpochDay get() = DailyPlayCountEntity.todayEpochDay()
        override val startEpochDay get() = endEpochDay - 364
    }

    data class Custom(
        override val startEpochDay: Long,
        override val endEpochDay: Long
    ) : StatsTimeRange()
}
