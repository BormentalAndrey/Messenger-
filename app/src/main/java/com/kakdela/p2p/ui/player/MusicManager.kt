package com.kakdela.p2p.ui.player

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.*
import com.kakdela.p2p.model.AudioTrack

object MusicManager {
    var tracks = mutableStateListOf<AudioTrack>()
    var currentIndex by mutableIntStateOf(-1)
    var isPlaying by mutableStateOf(false)
    private var mediaPlayer: MediaPlayer? = null

    val currentTrack: AudioTrack?
        get() = if (currentIndex in tracks.indices) tracks[currentIndex] else null

    fun playTrack(context: Context, index: Int) {
        if (index !in tracks.indices) return
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        try {
            mediaPlayer = MediaPlayer.create(context, tracks[index].uri).apply {
                setOnCompletionListener { playNext(context) }
                start()
            }
            currentIndex = index
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer == null) return
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            mediaPlayer?.start()
            isPlaying = true
        }
    }

    fun playNext(context: Context) {
        if (tracks.isEmpty()) return
        val nextIndex = (currentIndex + 1) % tracks.size
        playTrack(context, nextIndex)
    }

    fun playPrevious(context: Context) {
        if (tracks.isEmpty()) return
        val prevIndex = if (currentIndex - 1 < 0) tracks.size - 1 else currentIndex - 1
        playTrack(context, prevIndex)
    }
}
