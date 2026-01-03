package com.kakdela.p2p.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MiniPlayer(vm: PlayerViewModel) {
    val track by vm.current.collectAsState()
    track?.let {
        Surface {
            Text("▶ ${it.title}", Modifier.padding(12.dp))
        }
    }
}

