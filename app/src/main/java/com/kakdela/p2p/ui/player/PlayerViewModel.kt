package com.kakdela.p2p.ui.player

import android.app.*
import android.content.Intent
import androidx.lifecycle.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.kakdela.p2p.data.AudioRepository
import com.kakdela.p2p.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player = ExoPlayer.Builder(app).build()
    private val repo = AudioRepository(app)

    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: PlayerNotificationManager

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val filteredTracks = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    init {
        load()
        setupMediaSessionAndNotification()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _currentTrack.value = _tracks.value.getOrNull(player.currentMediaItemIndex)
            }
        })
    }

    private fun load() {
        viewModelScope.launch {
            val list = repo.loadTracks()
            _tracks.value = list
            player.setMediaItems(list.map { MediaItem.fromUri(it.uri) })
            player.prepare()
            _currentTrack.value = list.firstOrNull()
        }
    }

    fun playTrack(track: AudioTrack) {
        val index = _tracks.value.indexOf(track)
        if (index >= 0) {
            player.seekTo(index, 0)
            player.play()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = player.seekToNextMediaItem()

    fun previous() = player.seekToPreviousMediaItem()

    fun seekTo(pos: Long) = player.seekTo(pos)

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleRepeat() {
        player.repeatMode =
            if (player.repeatMode == Player.REPEAT_MODE_OFF)
                Player.REPEAT_MODE_ALL
            else Player.REPEAT_MODE_OFF
    }

    private fun setupMediaSessionAndNotification() {
        mediaSession = MediaSession.Builder(getApplication(), player).build()
    }

    override fun onCleared() {
        player.release()
        mediaSession.release()
        super.onCleared()
    }
}
