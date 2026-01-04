package com.kakdela.p2p.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.model.AudioTrack

// 🎵 Основной экран проигрывателя
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {

    // Пример треков (замени на реальные из твоей коллекции)
    val tracks = remember {
        listOf(
            AudioTrack(1, "Track 1", "Artist 1", "Album 1", 1, 200000, Uri.EMPTY),
            AudioTrack(2, "Track 2", "Artist 2", "Album 2", 2, 180000, Uri.EMPTY),
            AudioTrack(3, "Track 3", "Artist 3", "Album 3", 3, 240000, Uri.EMPTY),
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MP3 Проигрыватель", color = Color.Cyan) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks) { track ->
                TrackItem(track) {
                    // Тут можно добавить логику проигрывания
                    println("Clicked on track: ${track.title}")
                }
            }
        }
    }
}

// 🎵 Компонент трека
@Composable
fun TrackItem(track: AudioTrack, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Cyan)
        }
    }
}
