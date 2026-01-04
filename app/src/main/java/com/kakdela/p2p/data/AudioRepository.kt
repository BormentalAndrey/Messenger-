package com.kakdela.p2p.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.kakdela.p2p.model.AudioTrack

class AudioRepository(private val context: Context) {

    fun loadTracks(): List<AudioTrack> {
        val result = mutableListOf<AudioTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
        )

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC}=1",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1) ?: "Unknown"
                val artist = cursor.getString(2) ?: "Unknown"
                val albumTitle = cursor.getString(3) ?: "Unknown"
                val albumId = cursor.getLong(4)
                val duration = cursor.getLong(5)
                val trackNumber = cursor.getInt(6)

                if (duration < 10_000) continue

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val albumArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                result.add(
                    AudioTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        albumTitle = albumTitle,
                        trackNumber = trackNumber,
                        duration = duration,
                        uri = uri,
                        albumArt = albumArt,
                        albumId = albumId
                    )
                )
            }
        }
        return result
    }
}
