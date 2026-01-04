package com.kakdela.p2p.ui.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
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
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            MusicManager.tracks.clear()
            MusicManager.tracks.addAll(fetchAudioTracks(context))
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            if (MusicManager.tracks.isEmpty()) {
                MusicManager.tracks.addAll(fetchAudioTracks(context))
            }
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(MusicManager.tracks) { index, track ->
                    TrackItem(
                        track = track,
                        isCurrent = index == MusicManager.currentIndex,
                        isPlaying = index == MusicManager.currentIndex && MusicManager.isPlaying
                    ) {
                        if (MusicManager.currentIndex == index) MusicManager.togglePlayPause()
                        else MusicManager.playTrack(context, index)
                    }
                }
            }

            MusicManager.currentTrack?.let { track ->
                PlaybackControlPanel(
                    track = track,
                    isPlaying = MusicManager.isPlaying,
                    onPlayPause = { MusicManager.togglePlayPause() },
                    onNext = { MusicManager.playNext(context) },
                    onPrevious = { MusicManager.playPrevious(context) }
                )
            }
        }
    }
}

@Composable
fun TrackItem(track: AudioTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF252525) else Color(0xFF121212))
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = if (isCurrent) Color.Cyan else Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(
                if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                null, tint = if (isCurrent) Color.Cyan else Color.White
            )
        }
    }
}

@Composable
fun PlaybackControlPanel(track: AudioTrack, isPlaying: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = track.albumArt, contentDescription = null, modifier = Modifier.size(45.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Cyan, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White) }
                IconButton(onClick = onPlayPause, modifier = Modifier.background(Color.Cyan, RoundedCornerShape(50.dp))) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.Black)
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, null, tint = Color.White) }
            }
        }
    }
}

fun fetchAudioTracks(context: Context): List<AudioTrack> {
    val tracks = mutableListOf<AudioTrack>()
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID)
    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
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
            tracks.add(AudioTrack(
                id = id, title = cursor.getString(titleCol) ?: "Unknown", artist = cursor.getString(artistCol) ?: "Unknown", albumTitle = cursor.getString(albumCol) ?: "Unknown",
                trackNumber = cursor.getInt(trackNoCol), duration = cursor.getLong(durationCol), uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                albumArt = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId), albumId = albumId
            ))
        }
    }
    return tracks
}

