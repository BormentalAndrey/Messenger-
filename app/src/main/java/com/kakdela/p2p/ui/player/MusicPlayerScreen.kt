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

    // Состояние списка треков
    val tracks = remember { mutableStateListOf<AudioTrack>() }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Загрузка треков при запуске
    LaunchedEffect(Unit) {
        tracks.clear()
        tracks.addAll(fetchAudioTracks(context))
    }

    // Функции управления плеером
    fun playTrack(track: AudioTrack) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        try {
            mediaPlayer = MediaPlayer.create(context, track.uri).apply {
                setOnCompletionListener {
                    isPlaying = false
                }
                start()
            }
            currentTrack = track
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            // Список треков
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

            // Панель управления (внизу)
            currentTrack?.let { track ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(track.artist, color = Color.Gray, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (isPlaying) pauseTrack() else resumeTrack() }) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Cyan
                                )
                            }
                            IconButton(onClick = { stopTrack() }) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }

    // Очистка ресурсов при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}

@Composable
fun TrackItem(track: AudioTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF333333) else Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title, 
                    color = if (isCurrent) Color.Cyan else Color.White, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(text = track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(
                imageVector = if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isCurrent) Color.Cyan else Color.LightGray
            )
        }
    }
}

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
    
    context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val title = cursor.getString(titleIndex)
            val artist = cursor.getString(artistIndex)
            val album = cursor.getString(albumIndex)
            val duration = cursor.getLong(durationIndex)
            val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

            // Убедитесь, что конструктор AudioTrack совпадает с этим набором данных
            tracks.add(AudioTrack(id, title, artist, album, 0, duration, contentUri))
        }
    }
    return tracks
}

