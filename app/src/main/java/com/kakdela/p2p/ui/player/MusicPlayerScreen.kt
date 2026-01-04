package com.kakdela.p2p.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.model.AudioTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {
    val context = LocalContext.current

    // Получаем все аудиотреки с устройства
    val tracks = remember { mutableStateListOf<AudioTrack>() }

    LaunchedEffect(Unit) {
        tracks.clear()
        tracks.addAll(fetchAudioTracks(context))
    }

    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun playTrack(track: AudioTrack) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, track.uri).apply {
            setOnCompletionListener {
                isPlaying = false
            }
            start()
        }
        currentTrack = track
        isPlaying = true
    }

    fun pauseTrack() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    fun resumeTrack() {
        mediaPlayer?.start()
        isPlaying = true
    }

    fun stopTrack() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrack = null
        isPlaying = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MP3 Плеер", color = Color.Cyan) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tracks) { track ->
                    TrackItem(track = track, isCurrent = track == currentTrack, isPlaying = isPlaying) {
                        if (currentTrack == track) {
                            if (isPlaying) pauseTrack() else resumeTrack()
                        } else {
                            playTrack(track)
                        }
                    }
                }
            }

            // Панель управления текущим треком
            currentTrack?.let { track ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(Color(0xFF222222))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(track.artist, color = Color.Gray, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { pauseTrack() }) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.Cyan)
                        }
                        IconButton(onClick = { resumeTrack() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Cyan)
                        }
                        IconButton(onClick = { stopTrack() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }

    // Освобождение MediaPlayer при уничтожении
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
}

@Composable
fun TrackItem(track: AudioTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color.DarkGray else Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
                Text(track.albumTitle, color = Color.Gray, fontSize = 10.sp)
            }
            Icon(
                if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Cyan
            )
        }
    }
}

// Получение всех аудиотреков с устройства
fun fetchAudioTracks(context: Context): List<AudioTrack> {
    val tracks = mutableListOf<AudioTrack>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val cursor = context.contentResolver.query(uri, projection, selection, null, null)

    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (it.moveToNext()) {
            val id = it.getLong(idIndex)
            val title = it.getString(titleIndex)
            val artist = it.getString(artistIndex)
            val album = it.getString(albumIndex)
            val duration = it.getLong(durationIndex)
            val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

            tracks.add(AudioTrack(id, title, artist, album, 0, duration, contentUri))
        }
    }

    return tracks
}
