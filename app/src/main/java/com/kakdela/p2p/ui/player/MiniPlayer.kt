package com.kakdela.p2p.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun MiniPlayer() {
    val context = LocalContext.current
    val track = MusicManager.currentTrack
    val isPlaying = MusicManager.isPlaying

    // Если ничего не играет, не показываем плеер
    if (track == null) return

    Surface(
        tonalElevation = 8.dp,
        color = Color(0xFF121212),
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (abs(dragAmount) > 50) {
                        if (dragAmount > 0) MusicManager.playPrevious(context) 
                        else MusicManager.playNext(context)
                    }
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier.size(48.dp).background(Color.DarkGray)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(track.title, fontSize = 14.sp, color = Color.White, maxLines = 1)
                Text(track.artist, fontSize = 11.sp, color = Color.Cyan, maxLines = 1)
            }

            IconButton(onClick = { MusicManager.playPrevious(context) }) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
            }
            IconButton(onClick = { MusicManager.togglePlayPause() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = Color.Cyan
                )
            }
            IconButton(onClick = { MusicManager.playNext(context) }) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White)
            }
        }
    }
}
