/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.fragments.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentStatisticsBinding
import code.name.monkey.retromusic.extensions.applyToolbar
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.stats.StatsRowBinder
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Statistics screen (see CLAUDE.md, Component 2; redesigned in Component 6's pivot away
 * from charts). No genre legend row, no pie/bar chart toggle, no time window anymore -- just
 * a ranked "Top genres" list against [StatisticsViewModel], which is backed by real
 * per-genre playtime as of Component 7 (see that class's doc comment).
 */
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyToolbar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupComponent3Buttons()
        observeViewModel()

    }

    private fun setupComponent3Buttons() {
        binding.statsArtistsButton.setOnClickListener {
            findNavController().navigate(R.id.statsArtistsFragment)
        }
        binding.statsAlbumsButton.setOnClickListener {
            findNavController().navigate(R.id.statsAlbumsFragment)
        }
    }

    private fun observeViewModel() {
        viewModel.totalPlaytimeMillis.observe(viewLifecycleOwner) { totalMillis ->
            binding.totalPlaytimeText.text = MusicUtil.getReadableDurationString(totalMillis)
        }
        viewModel.displayGenreStats.observe(viewLifecycleOwner) { stats ->
            binding.genreListContainer.removeAllViews()
            stats.forEachIndexed { index, stat ->
                val name = if (stat.id == StatisticsViewModel.OTHERS_ID) {
                    getString(R.string.others)
                } else {
                    stat.name
                }
                StatsRowBinder.addRankRow(
                    layoutInflater, binding.genreListContainer, index + 1, name, stat.playedMillis
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
