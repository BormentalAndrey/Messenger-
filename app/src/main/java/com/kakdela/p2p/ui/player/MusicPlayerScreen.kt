package com.kakdela.p2p.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import android.Manifest
import android.os.Build

private fun Long.formatDuration(): String {
    val minutes = this / 60000
    val seconds = (this % 60000) / 1000
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun MusicPlayerScreen(vm: PlayerViewModel = viewModel()) {
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.loadTracks() // перезагружаем при получении разрешения
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        }
    }

    val tracks by vm.tracks.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.currentPosition.collectAsState()
    val duration by vm.currentDuration.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffleEnabled.collectAsState()

    Scaffold(
        bottomBar = { MiniPlayer(vm = vm) }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Нет аудиофайлов или нет разрешения на чтение")
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                item {
                    // Текущий трек (большая карточка)
                    current?.let { track ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = track.albumArt,
                                contentDescription = null,
                                modifier = Modifier.size(300.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(track.artist, fontSize = 18.sp)
                            Spacer(Modifier.height(16.dp))

                            // Прогресс
                            Slider(
                                value = position.toFloat(),
                                onValueChange = { vm.seekTo(it.toLong()) },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(position.formatDuration())
                                Text(duration.formatDuration())
                            }

                            Spacer(Modifier.height(24.dp))

                            // Управление
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { vm.toggleShuffle() }) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        null,
                                        tint = if (shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
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
                                IconButton(onClick = { vm.toggleRepeat() }) {
                                    Icon(
                                        when (repeatMode) {
                                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                            else -> Icons.Default.Repeat
                                        },
                                        null,
                                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }
                }

                items(tracks) { track ->
                    ListItem(
                        headlineContent = { Text(track.title) },
                        supportingContent = { Text(track.artist) },
                        leadingContent = {
                            AsyncImage(
                                model = track.albumArt,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                        },
                        modifier = Modifier
                            .clickable { vm.playTrack(track) }
                            .background(if (track == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}
