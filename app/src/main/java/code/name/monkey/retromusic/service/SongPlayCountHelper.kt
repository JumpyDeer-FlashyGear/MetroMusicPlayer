/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.service

import android.util.Log
import code.name.monkey.retromusic.helper.StopWatch
import code.name.monkey.retromusic.model.Song

class SongPlayCountHelper {

    private val stopWatch = StopWatch()
    private var lastSavedTime: Long = 0
    var song = Song.emptySong
        private set

    fun shouldBumpPlayCount(): Boolean {
        return song.duration * 0.5 < stopWatch.elapsedTime
    }

    val elapsedTime: Long
        get() = stopWatch.elapsedTime

    fun songElapsedTime(): Long {
        val songElapsedTime = stopWatch.elapsedTime - lastSavedTime
        isSongSaved = true
        // Log.d("PlayTimeDebug", "Restarted StopWatch Last: $lastSavedTime Current: $songElapsedTime")
        lastSavedTime = stopWatch.elapsedTime
        return if (songElapsedTime > 100000000) {
            0L
        } else {
            songElapsedTime
        }
    }

    var isSongSaved : Boolean = false

    

    fun notifySongChanged(song: Song) {
        synchronized(this) {
            stopWatch.restart()
            isSongSaved = false
            lastSavedTime = 0L
            Log.d("PlayTimeDebug", "Reset StopWatch")
            this.song = song
        }
    }

    fun notifyPlayStateChanged(isPlaying: Boolean) {
        synchronized(this) {
            if (isPlaying) {
                Log.d("PlayTimeDebug", "Started StopWatch")
                stopWatch.start()
                isSongSaved = false
            } else {
                stopWatch.pause()
                Log.d("PlayTimeDebug", "Paused StopWatch")
            }
        }
    }

    companion object {
        val TAG: String = SongPlayCountHelper::class.java.simpleName
    }
}
