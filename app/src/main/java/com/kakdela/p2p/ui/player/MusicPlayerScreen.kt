package com.kakdela.p2p.ui.player

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kakdela.p2p.model.AudioTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf<AudioTrack>() }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        tracks.clear()
        tracks.addAll(fetchAudioTracks(context))
    }

    fun playTrack(track: AudioTrack) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        try {
            mediaPlayer = MediaPlayer.create(context, track.uri).apply {
                setOnCompletionListener { isPlaying = false }
                start()
            }
            currentTrack = track
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseTrack() = mediaPlayer?.pause().also { isPlaying = false }
    fun resumeTrack() = mediaPlayer?.start().also { isPlaying = true }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Моя Музыка", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items = tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isCurrent = (track.id == currentTrack?.id),
                        isPlaying = isPlaying
                    ) {
                        if (currentTrack?.id == track.id) {
                            if (isPlaying) pauseTrack() else resumeTrack()
                        } else {
                            playTrack(track)
                        }
                    }
                }
            }

            currentTrack?.let { track ->
                PlaybackControlPanel(
                    track = track,
                    isPlaying = isPlaying,
                    onPlayPause = { if (isPlaying) pauseTrack() else resumeTrack() }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }
}

@Composable
fun TrackItem(track: AudioTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF252525) else Color(0xFF121212)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка альбома
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrent) Color.Cyan else Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(text = track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }

            Icon(
                imageVector = if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isCurrent) Color.Cyan else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun PlaybackControlPanel(track: AudioTrack, isPlaying: Boolean, onPlayPause: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Cyan, fontSize = 12.sp, maxLines = 1)
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.background(Color.Cyan, RoundedCornerShape(50.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
            }
        }
    }
}

fun fetchAudioTracks(context: Context): List<AudioTrack> {
    val tracks = mutableListOf<AudioTrack>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID
    )

    context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val trackNoCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val albumId = cursor.getLong(albumIdCol)
            
            // Формируем URI для картинки альбома
            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
            val albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)

            tracks.add(
                AudioTrack(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    albumTitle = cursor.getString(albumCol) ?: "Unknown Album",
                    trackNumber = cursor.getInt(trackNoCol),
                    duration = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    albumArt = albumArtUri,
                    albumId = albumId
                )
            )
        }
    }
    return tracks
}

