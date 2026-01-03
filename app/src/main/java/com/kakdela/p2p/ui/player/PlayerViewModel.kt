package com.kakdela.p2p.ui.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Простая модель трека
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val url: String = "" // Для примера
)

class PlayerViewModel : ViewModel() {

    // Список треков (заглушка для демонстрации)
    val tracks = listOf(
        Track("1", "Neon Vibes", "Cyber Artist"),
        Track("2", "Night Drive", "Synthwave Hero"),
        Track("3", "Coding Flow", "Dev Music"),
        Track("4", "Relax", "Chill Zone")
    )

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Логика воспроизведения
    fun play(track: Track) {
        _current.value = track
        _isPlaying.value = true
        // Здесь была бы логика MediaPlayer
    }

    fun pause() {
        _isPlaying.value = false
        // mediaPlayer.pause()
    }

    fun resume() {
        if (_current.value != null) {
            _isPlaying.value = true
            // mediaPlayer.start()
        }
    }

    fun next() {
        val currentTrack = _current.value ?: return
        val index = tracks.indexOf(currentTrack)
        if (index != -1 && index < tracks.lastIndex) {
            play(tracks[index + 1])
        } else {
            // Циклически к началу
            play(tracks[0])
        }
    }

    fun prev() {
        val currentTrack = _current.value ?: return
        val index = tracks.indexOf(currentTrack)
        if (index > 0) {
            play(tracks[index - 1])
        } else {
            play(tracks.last())
        }
    }
}

