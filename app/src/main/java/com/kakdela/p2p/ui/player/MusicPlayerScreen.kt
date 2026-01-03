package com.kakdela.p2p.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import android.Manifest
import android.os.Build
import kotlinx.coroutines.launch

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(vm: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Разрешения (как раньше)
    // ... (код запроса READ_MEDIA_AUDIO и POST_NOTIFICATIONS без изменений)

    val allTracks by vm.filteredTracks.collectAsState()
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val favorites by vm.favoriteTracks.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.currentPosition.collectAsState()
    val duration by vm.currentDuration.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffleEnabled.collectAsState()
    val sleepRemaining by vm.sleepTimeRemaining.collectAsState()
    val bass by vm.bassStrength.collectAsState()
    val virt by vm.virtualizerStrength.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 4 })
    val tabs = listOf("Треки", "Альбомы", "Артисты", "Избранное")

    val sheetState = rememberModalBottomSheetState()
    var showEqSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Музыкальный плеер") },
                actions = {
                    IconButton(onClick = { showEqSheet = true }) {
                        Icon(Icons.Default.GraphicEq, "Эквалайзер")
                    }
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(Icons.Default.Timer, "Таймер сна")
                    }
                }
            )
        },
        bottomBar = { MiniPlayer(vm = vm, sleepRemaining = sleepRemaining) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Поиск только для таба Треки
            if (pagerState.currentPage == 0) {
                OutlinedTextField(
                    value = vm.searchQuery.value,
                    onValueChange = { vm.setSearchQuery(it) },
                    label = { Text("Поиск...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> TrackList(allTracks, current, vm)
                    1 -> AlbumList(albums, current, vm)
                    2 -> ArtistList(artists, current, vm)
                    3 -> TrackList(favorites, current, vm)
                }
            }

            // Большая карточка текущего трека (всегда внизу перед мини-плеером)
            current?.let { track ->
                Card(Modifier.padding(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(model = track.albumArt, contentDescription = null, modifier = Modifier.size(300.dp))
                        Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${track.artist} • ${track.albumTitle}", fontSize = 18.sp)
                        Slider(value = position.toFloat(), onValueChange = { vm.seekTo(it.toLong()) }, valueRange = 0f..duration.toFloat().coerceAtLeast(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(position.formatDuration())
                            Text(duration.formatDuration())
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { vm.toggleShuffle() }) {
                                Icon(Icons.Default.Shuffle, null, tint = if (shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                            }
                            IconButton(onClick = { vm.previous() }) { Icon(Icons.Default.SkipPrevious, null) }
                            IconButton(onClick = { vm.togglePlayPause() }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            }
                            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, null) }
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
        }
    }

    if (showEqSheet) {
        ModalBottomSheet(onDismissRequest = { showEqSheet = false }, sheetState = sheetState) {
            Column(Modifier.padding(16.dp)) {
                Text("Аудиоэффекты", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text("Бас")
                Slider(value = bass.toFloat(), onValueChange = { vm.setBassStrength(it.toInt()) }, valueRange = 0f..1000f)
                Text("3D-звук (виртуализация)")
                Slider(value = virt.toFloat(), onValueChange = { vm.setVirtualizerStrength(it.toInt()) }, valueRange = 0f..1000f)
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                val presets = listOf(15, 30, 45, 60)
                Column {
                    presets.forEach { min ->
                        Text("${min} минут", modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.startSleepTimer(min.toLong())
                                showSleepDialog = false
                            }
                            .padding(8.dp))
                    }
                    if (sleepRemaining > 0) {
                        Text("Отменить таймер", modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.cancelSleepTimer()
                                showSleepDialog = false
                            }
                            .padding(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
fun TrackList(tracks: List<AudioTrack>, current: AudioTrack?, vm: PlayerViewModel) {
    LazyColumn {
        items(tracks) { track ->
            ListItem(
                headlineContent = { Text(track.title) },
                supportingContent = { Text("${track.artist} • ${track.albumTitle}") },
                leadingContent = { AsyncImage(model = track.albumArt, contentDescription = null, modifier = Modifier.size(56.dp)) },
                trailingContent = {
                    IconButton(onClick = { vm.toggleFavorite(track.id) }) {
                        Icon(
                            if (track.id in vm.favoriteIds.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null
                        )
                    }
                },
                modifier = Modifier
                    .clickable { vm.playTrack(track) }
                    .background(if (track == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
fun AlbumList(albums: List<Album>, current: AudioTrack?, vm: PlayerViewModel) {
    LazyColumn {
        items(albums) { album ->
            ListItem(
                headlineContent = { Text(album.title) },
                supportingContent = { Text(album.artist) },
                leadingContent = { AsyncImage(model = album.albumArt, contentDescription = null, modifier = Modifier.size(56.dp)) },
                modifier = Modifier
                    .clickable { vm.playAlbum(album) }
                    .background(if (album.tracks.contains(current)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
fun ArtistList(artists: List<Artist>, current: AudioTrack?, vm: PlayerViewModel) {
    LazyColumn {
        items(artists) { artist ->
            ListItem(
                headlineContent = { Text(artist.name) },
                supportingContent = { Text("${artist.tracks.size} треков") },
                modifier = Modifier
                    .clickable { vm.playArtist(artist) }
                    .background(if (artist.tracks.contains(current)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            )
        }
    }
}
