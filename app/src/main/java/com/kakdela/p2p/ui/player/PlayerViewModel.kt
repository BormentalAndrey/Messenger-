package com.kakdela.p2p.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.kakdela.p2p.data.AudioRepository // Убедитесь, что репозиторий здесь
import com.kakdela.p2p.model.AudioTrack      // Убедитесь, что модель здесь
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // Ленивая инициализация плеера
    private val player = ExoPlayer.Builder(app).build()
    private val repo = AudioRepository(app)

    private var mediaSession: MediaSession? = null

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val filteredTracks = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    init {
        setupPlayer()
        load()
        setupMediaSession()
        startProgressUpdate()
    }

    private fun setupPlayer() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // Синхронизируем текущий трек по индексу из списка
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex in _tracks.value.indices) {
                    _currentTrack.value = _tracks.value[currentIndex]
                }
            }
        })
    }

    private fun load() {
        viewModelScope.launch {
            val list = repo.loadTracks()
            _tracks.value = list

            val mediaItems = list.map { track ->
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaId(track.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .build()
                    )
                    .build()
            }

            player.setMediaItems(mediaItems)
            player.prepare()
            if (list.isNotEmpty()) {
                _currentTrack.value = list.first()
            }
        }
    }

    // Обновление прогресса для UI слайдера
    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                }
                delay(500) // Обновляем раз в полсекунды
            }
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
    
    fun seekTo(pos: Long) {
        player.seekTo(pos)
        _currentPosition.value = pos
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleRepeat() {
        player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_OFF)
            Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(getApplication(), player).build()
    }

    override fun onCleared() {
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onCleared()
    }

    // Вспомогательный метод для получения длительности
    fun getDuration(): Long = player.duration.coerceAtLeast(0L)
}

