package com.kakdela.p2p.data

import android.content.Context
import android.provider.MediaStore
import com.kakdela.p2p.model.AudioTrack

class AudioRepository(private val context: Context) {

    fun loadTracks(): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            ),
            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                list.add(
                    AudioTrack(
                        id = id,
                        title = it.getString(1),
                        artist = it.getString(2),
                        duration = it.getLong(3),
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            .buildUpon().appendPath(id.toString()).build()
                    )
                )
            }
        }
        return list
    }
}
