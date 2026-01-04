package com.kakdela.p2p.ui.player

import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun MiniPlayer(vm: PlayerViewModel) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    track ?: return

    Surface(
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, drag ->
                    if (abs(drag) > 100) {
                        if (drag > 0) vm.previous() else vm.next()
                    }
                }
            }
            .clickable { }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track!!.albumArt,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                MarqueeText(track!!.title, 15.sp, Color.White)
                Text(track!!.artist, fontSize = 12.sp)
            }

            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Default.SkipPrevious, null)
            }
            IconButton(onClick = { vm.togglePlayPause() }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null
                )
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Default.SkipNext, null)
            }
        }
    }
}

@Composable
fun MarqueeText(text: String, fontSize: TextUnit, color: Color) {
    Text(
        text,
        fontSize = fontSize,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
