package com.kakdela.p2p.ui.player

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import com.kakdela.p2p.model.AudioTrack

object MusicManager {
    var tracks = mutableStateListOf<AudioTrack>()
    var currentIndex by mutableIntStateOf(-1)
    var isPlaying by mutableStateOf(false)
    private var mediaPlayer: MediaPlayer? = null

    val currentTrack: AudioTrack?
        get() = if (currentIndex in tracks.indices) tracks[currentIndex] else null

    // ✅ Эту функцию вызывает MainActivity
    fun loadTracks(context: Context) {
        val fetched = fetchAudioTracksFromSystem(context)
        tracks.clear()
        tracks.addAll(fetched)
    }

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

    // Внутренняя логика сканирования
    private fun fetchAudioTracksFromSystem(context: Context): List<AudioTrack> {
        val tempTracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), 
                    albumId
                )

                tempTracks.add(
                    AudioTrack(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        albumTitle = cursor.getString(albumCol) ?: "Unknown Album",
                        trackNumber = 0,
                        duration = cursor.getLong(durationCol),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        albumArt = albumArtUri,
                        albumId = albumId
                    )
                )
            }
        }
        return tempTracks
    }
}
