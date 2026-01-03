package com.kakdela.p2p.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MiniPlayer(vm: PlayerViewModel, onClick: () -> Unit = {}) {
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()

    current?.let { track ->
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.albumArt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1)
                    Text(track.artist, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Default.SkipPrevious, null)
                }
                IconButton(onClick = { vm.togglePlayPause() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Default.SkipNext, null)
                }
            }
        }
    }
}
