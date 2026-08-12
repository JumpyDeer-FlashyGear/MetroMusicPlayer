package code.name.monkey.retromusic.util.stats

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.util.MusicUtil

/**
 * Shared row-binding helpers for the Statistics screens' plain-list redesign (see CLAUDE.md,
 * Component 6 -- the pivot away from pie/bar charts). Used by the main screen's Top Genres
 * list, the Artist Detail screen's Overview / Top Albums / Top Songs sections, and the Album
 * Detail screen's Overview / Top Songs sections, so formatting and the rank-tier sizing rule
 * stay identical across every list rather than drifting between per-screen copies.
 */
object StatsRowBinder {

    /**
     * Inflates and appends one `item_stats_rank_row.xml` row to [container]: "{rank}. {name}"
     * on the left, formatted playtime on the right. Text size follows a 3-tier scheme --
     * rank 1 big, ranks 2-3 medium, rank 4+ small and uniform for the remainder -- confirmed
     * for every ranked list on these screens, see CLAUDE.md.
     */
    fun addRankRow(
        inflater: LayoutInflater,
        container: ViewGroup,
        rank: Int,
        name: String,
        playedMillis: Long
    ) {
        val row = inflater.inflate(R.layout.item_stats_rank_row, container, false)
        val nameView = row.findViewById<TextView>(R.id.rankName)
        val valueView = row.findViewById<TextView>(R.id.rankValue)
        nameView.text = row.context.getString(R.string.stats_rank_row_label, rank, name)
        valueView.text = MusicUtil.getReadableDurationString(playedMillis)
        val style = styleForRank(rank)
        TextViewCompat.setTextAppearance(nameView, style)
        TextViewCompat.setTextAppearance(valueView, style)
        container.addView(row)
    }

    /** Inflates and appends one `item_stats_overview_row.xml` label/value row to [container]. */
    fun addOverviewRow(inflater: LayoutInflater, container: ViewGroup, label: String, value: String) {
        val row = inflater.inflate(R.layout.item_stats_overview_row, container, false)
        row.findViewById<TextView>(R.id.overviewLabel).text = label
        row.findViewById<TextView>(R.id.overviewValue).text = value
        container.addView(row)
    }

    private fun styleForRank(rank: Int): Int = when {
        rank == 1 -> R.style.StatsRankBig
        rank <= 3 -> R.style.StatsRankMedium
        else -> R.style.StatsRankSmall
    }
}
