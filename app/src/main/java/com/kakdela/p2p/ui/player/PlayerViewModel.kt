package com.kakdela.p2p.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kakdela.p2p.model.AudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player: ExoPlayer = ExoPlayer.Builder(getApplication()).build()

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    init {
        loadTracks()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                _currentTrack.value = _tracks.value.getOrNull(index)
            }
        })

        viewModelScope.launch {
            while (true) {
                _currentPosition.value = player.currentPosition
                _currentDuration.value = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun loadTracks() {
        viewModelScope.launch {
            val tracksList = mutableListOf<AudioTrack>()

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )

            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            getApplication<Application>().contentResolver.query(
                collection, projection, null, null, sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "Unknown"
                    val duration = cursor.getLong(durationColumn)
                    if (duration < 10000) continue // пропускаем слишком короткие

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumId = cursor.getLong(albumIdColumn)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )

                    tracksList.add(AudioTrack(id, title, artist, duration, contentUri, albumArtUri))
                }
            }

            _tracks.value = tracksList

            if (tracksList.isNotEmpty()) {
                val mediaItems = tracksList.map { MediaItem.fromUri(it.uri) }
                player.setMediaItems(mediaItems)
                player.prepare()
                player.repeatMode = _repeatMode.value
                player.shuffleModeEnabled = _shuffleEnabled.value
                _currentTrack.value = tracksList[0]
            }
        }
    }

    fun playTrack(track: AudioTrack) {
        val index = _tracks.value.indexOf(track)
        if (index != -1) {
            player.seekTo(index, 0L)
            player.playWhenReady = true
        }
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.isPlaying
    }

    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode
        player.repeatMode = newMode
    }

    fun toggleShuffle() {
        val new = !_shuffleEnabled.value
        _shuffleEnabled.value = new
        player.shuffleModeEnabled = new
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
