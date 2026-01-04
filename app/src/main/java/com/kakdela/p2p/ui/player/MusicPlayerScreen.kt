package com.kakdela.p2p.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kakdela.p2p.model.AudioTrack

@Composable
fun MusicPlayerScreen(
    vm: PlayerViewModel = viewModel()
) {
    val tracks by vm.filteredTracks.collectAsState()
    val current by vm.currentTrack.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Scaffold(
            containerColor = Color.Black,
            bottomBar = { MiniPlayer(vm) }
        ) { padding ->

            if (tracks.isEmpty()) {
                // 👇 ВАЖНО: теперь не будет "чёрного экрана"
                EmptyMusicState(Modifier.padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(tracks) { track ->
                        TrackItem(
                            track = track,
                            isCurrent = track == current,
                            onClick = { vm.playTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMusicState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Музыка не найдена", color = Color.White)
            Text(
                "Проверь разрешение и наличие MP3",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
