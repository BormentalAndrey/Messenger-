package com.kakdela.p2p.model

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val trackNumber: Int,
    val duration: Long,
    val uri: Uri,
    val albumArt: Uri? = null
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArt: Uri?,
    val tracks: List<AudioTrack>
)

data class Artist(
    val name: String,
    val tracks: List<AudioTrack>
)
