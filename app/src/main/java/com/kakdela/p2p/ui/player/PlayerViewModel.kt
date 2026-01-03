package com.kakdela.p2p.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerNotificationManager.MediaDescriptionAdapter
import androidx.media3.ui.PlayerNotificationManager.BitmapCallback
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.kakdela.p2p.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        setupMediaSessionAndNotification()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение музыки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Управление плеером"
            }
            val manager = getApplication<Application>().getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSessionAndNotification() {
        createNotificationChannel()

        mediaSession = MediaSession.Builder(getApplication(), player).build()

        val descriptionAdapter = object : MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return _currentTrack.value?.title ?: "Неизвестно"
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return _currentTrack.value?.artist
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = getApplication<Application>().packageManager
                    .getLaunchIntentForPackage(getApplication<Application>().packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return PendingIntent.getActivity(
                    getApplication(),
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            override fun getCurrentLargeIcon(player: Player, callback: BitmapCallback): Bitmap? {
                val uri = _currentTrack.value?.albumArt ?: return null

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val loader = ImageLoader(getApplication())
                        val request = ImageRequest.Builder(getApplication())
                            .data(uri)
                            .size(512)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as BitmapDrawable).bitmap
                            callback.onBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        // игнорируем ошибку загрузки обложки
                    }
                }
                return null
            }
        }

        notificationManager = PlayerNotificationManager.Builder(
            getApplication(),
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(descriptionAdapter)
            .setSmallIconResourceId(android.R.drawable.ic_media_play) // замените на свой @drawable/ic_music_note
            .build()

        notificationManager.setMediaSessionToken(mediaSession.sessionCompatToken)
        notificationManager.setPlayer(player)
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
                    if (duration < 10000) continue

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

    // Остальные функции (playTrack, togglePlayPause, next, previous, seekTo, toggleRepeat, toggleShuffle) остаются без изменений

    override fun onCleared() {
        notificationManager.setPlayer(null)
        mediaSession.release()
        player.release()
        super.onCleared()
    }
}
