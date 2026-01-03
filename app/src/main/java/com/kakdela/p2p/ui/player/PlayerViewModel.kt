package com.kakdela.p2p.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.kakdela.p2p.model.Album
import com.kakdela.p2p.model.Artist
import com.kakdela.p2p.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.ContentUris

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val CHANNEL_ID = "p2p_music_channel"
        private const val NOTIFICATION_ID = 100
    }

    private val player: ExoPlayer = ExoPlayer.Builder(getApplication()).build()

    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: PlayerNotificationManager

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var sleepTimerJob: Job? = null

    private val _allTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val allTracks: StateFlow<List<AudioTrack>> = _allTracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTracks: StateFlow<List<AudioTrack>> = combine(_searchQuery, _allTracks) { query, tracks ->
        if (query.isEmpty()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _favoriteIds = MutableStateFlow(mutableSetOf<Long>())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    val favoriteTracks: StateFlow<List<AudioTrack>> = combine(_allTracks, _favoriteIds) { tracks, favs ->
        tracks.filter { it.id in favs }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    private val _sleepTimeRemaining = MutableStateFlow(0L)
    val sleepTimeRemaining: StateFlow<Long> = _sleepTimeRemaining.asStateFlow()

    private val _bassStrength = MutableStateFlow(500) // 0-1000
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(500) // 0-1000
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    init {
        loadTracks()
        setupMediaSessionAndNotification()
        setupAudioEffects()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                _currentTrack.value = _allTracks.value.getOrNull(index)
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

    private fun setupAudioEffects() {
        viewModelScope.launch {
            player.prepare()
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNAVAILABLE) {
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = true
                    setStrength(_bassStrength.value.toShort())
                }
                virtualizer = Virtualizer(0, sessionId).apply {
                    enabled = true
                    setStrength(_virtualizerStrength.value.toShort())
                }
            }
        }
    }

    fun setBassStrength(strength: Int) {
        _bassStrength.value = strength
        bassBoost?.setStrength(strength.toShort())
    }

    fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        virtualizer?.setStrength(strength.toShort())
    }

    fun startSleepTimer(minutes: Long) {
        sleepTimerJob?.cancel()
        val millis = minutes * 60_000L
        _sleepTimeRemaining.value = millis
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimeRemaining.value > 0) {
                delay(1000)
                _sleepTimeRemaining.value -= 1000
            }
            player.playWhenReady = false
            _sleepTimeRemaining.value = 0
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimeRemaining.value = 0
    }

    fun toggleFavorite(trackId: Long) {
        val set = _favoriteIds.value.toMutableSet()
        if (trackId in set) set.remove(trackId) else set.add(trackId)
        _favoriteIds.value = set
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(track: AudioTrack) {
        val index = _allTracks.value.indexOf(track)
        if (index != -1) {
            player.seekTo(index, 0L)
            player.playWhenReady = true
        }
    }

    fun playAlbum(album: Album) {
        val firstTrack = album.tracks.firstOrNull() ?: return
        playTrack(firstTrack)
    }

    fun playArtist(artist: Artist) {
        val firstTrack = artist.tracks.firstOrNull() ?: return
        playTrack(firstTrack)
    }

    // Остальные функции (togglePlayPause, next, previous, seekTo, toggleRepeat, toggleShuffle) без изменений

    @SuppressLint("InlinedApi")
    private fun loadTracks() {
        viewModelScope.launch {
            val tracksList = mutableListOf<AudioTrack>()

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK
            )

            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            getApplication<Application>().contentResolver.query(
                collection, projection, null, null, sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "<unknown>"
                    val albumTitle = cursor.getString(albumColumn) ?: "Unknown"
                    val duration = cursor.getLong(durationColumn)
                    val trackNumber = cursor.getInt(trackColumn)
                    if (duration < 10000) continue

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumId = cursor.getLong(albumIdColumn)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )

                    tracksList.add(AudioTrack(id, title, artist, albumTitle, trackNumber, duration, contentUri, albumArtUri))
                }
            }

            _allTracks.value = tracksList.sortedBy { it.title }

            val albumsList = tracksList.groupBy { it.albumId }.map { (albumId, tr) ->
                val first = tr.first()
                Album(albumId, first.albumTitle, first.artist, first.albumArt, tr.sortedBy { it.trackNumber })
            }.sortedBy { it.title }

            val artistsList = tracksList.groupBy { it.artist }.filterKeys { it != "<unknown>" }.map { (name, tr) ->
                Artist(name, tr.sortedBy { it.albumTitle + it.trackNumber.toString().padStart(5, '0') })
            }.sortedBy { it.name }

            _albums.value = albumsList
            _artists.value = artistsList

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

    // setupMediaSessionAndNotification() без изменений (из предыдущего ответа)

    override fun onCleared() {
        sleepTimerJob?.cancel()
        bassBoost?.release()
        virtualizer?.release()
        notificationManager.setPlayer(null)
        mediaSession.release()
        player.release()
        super.onCleared()
    }
}
