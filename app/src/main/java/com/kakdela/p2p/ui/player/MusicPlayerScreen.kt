package com.kakdela.p2p.ui.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.kakdela.p2p.model.*

@Composable
fun MusicPlayerScreen(vm: PlayerViewModel = viewModel()) {

    val tracks by vm.filteredTracks.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    Scaffold(
        bottomBar = { MiniPlayer(vm) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(tracks) { track ->
                ListItem(
                    headlineContent = { Text(track.title) },
                    supportingContent = { Text(track.artist) },
                    leadingContent = {
                        AsyncImage(
                            model = track.albumArt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    modifier = Modifier
                        .clickable { vm.playTrack(track) }
                        .background(
                            if (track == current)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                )
            }
        }
    }
}
