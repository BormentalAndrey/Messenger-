package com.kakdela.p2p.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MusicPlayerScreen(vm: PlayerViewModel = viewModel()) {

    val current by vm.current.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Text(
            text = current?.title ?: "Музыкальный плеер",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton({ vm.prev() }) { Icon(Icons.Default.SkipPrevious, null) }
            IconButton({ vm.pause() }) { Icon(Icons.Default.Pause, null) }
            IconButton({ vm.resume() }) { Icon(Icons.Default.PlayArrow, null) }
            IconButton({ vm.next() }) { Icon(Icons.Default.SkipNext, null) }
        }

        Divider(Modifier.padding(vertical = 12.dp))

        vm.tracks.forEach {
            Text(
                text = "${it.title} — ${it.artist}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.play(it) }
                    .padding(8.dp)
            )
        }
    }
}
