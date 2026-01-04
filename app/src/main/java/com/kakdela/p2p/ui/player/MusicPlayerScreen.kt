package com.kakdela.p2p.ui.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kakdela.p2p.model.AudioTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf<AudioTrack>() }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val currentTrack = if (currentIndex != -1 && currentIndex < tracks.size) tracks[currentIndex] else null

    // Функция запуска трека
    fun playTrack(index: Int) {
        if (index !in tracks.indices) return
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        try {
            val track = tracks[index]
            val mp = MediaPlayer.create(context, track.uri)
            mp.setOnCompletionListener {
                // Автоматическое переключение на следующий трек
                val nextIndex = (index + 1) % tracks.size
                playTrack(nextIndex)
            }
            mp.start()
            mediaPlayer = mp
            currentIndex = index
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val playNext = {
        if (tracks.isNotEmpty()) {
            val nextIndex = (currentIndex + 1) % tracks.size
            playTrack(nextIndex)
        }
    }

    val playPrevious = {
        if (tracks.isNotEmpty()) {
            val prevIndex = if (currentIndex - 1 < 0) tracks.size - 1 else currentIndex - 1
            playTrack(prevIndex)
        }
    }

    // Разрешения
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            tracks.clear()
            tracks.addAll(fetchAudioTracks(context))
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            tracks.clear()
            tracks.addAll(fetchAudioTracks(context))
        } else {
            launcher.launch(permission)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Моя Музыка", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.Black)) {
            if (tracks.isEmpty()) {
                Text("Треки не найдены", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                            TrackItem(
                                track = track,
                                isCurrent = (index == currentIndex),
                                isPlaying = (index == currentIndex && isPlaying)
                            ) {
                                if (currentIndex == index) {
                                    if (isPlaying) {
                                        mediaPlayer?.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer?.start()
                                        isPlaying = true
                                    }
                                } else {
                                    playTrack(index)
                                }
                            }
                        }
                    }

                    currentTrack?.let { track ->
                        PlaybackControlPanel(
                            track = track,
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (isPlaying) {
                                    mediaPlayer?.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer?.start()
                                    isPlaying = true
                                }
                            },
                            onNext = playNext,
                            onPrevious = playPrevious
                        )
                    }
                }
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
        )
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = if (isCurrent) Color.Cyan else Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(
                imageVector = if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isCurrent) Color.Cyan else Color.White
            )
        }
    }
}

@Composable
fun PlaybackControlPanel(
    track: AudioTrack,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier.size(45.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Cyan, fontSize = 12.sp)
            }
            
            // Кнопки управления
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White)
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(40.dp).background(Color.Cyan, RoundedCornerShape(50.dp))
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

fun fetchAudioTracks(context: Context): List<AudioTrack> {
    val tracks = mutableListOf<AudioTrack>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TRACK,
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
        val trackNoCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val albumId = cursor.getLong(albumIdCol)
            val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

            tracks.add(AudioTrack(
                id = id,
                title = cursor.getString(titleCol) ?: "Unknown",
                artist = cursor.getString(artistCol) ?: "Unknown Artist",
                albumTitle = cursor.getString(albumCol) ?: "Unknown Album",
                trackNumber = cursor.getInt(trackNoCol),
                duration = cursor.getLong(durationCol),
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                albumArt = albumArtUri,
                albumId = albumId
            ))
        }
    }
    return tracks
}

