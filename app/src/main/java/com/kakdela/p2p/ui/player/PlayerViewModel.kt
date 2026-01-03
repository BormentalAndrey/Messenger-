package com.kakdela.p2p.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.kakdela.p2p.data.AudioRepository
import com.kakdela.p2p.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player = ExoPlayer.Builder(app).build()
    val tracks = AudioRepository(app).loadTracks()

    private val _current = MutableStateFlow<AudioTrack?>(null)
    val current = _current.asStateFlow()

    fun play(track: AudioTrack) {
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        _current.value = track
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun next() = player.seekToNext()
    fun prev() = player.seekToPrevious()
}
