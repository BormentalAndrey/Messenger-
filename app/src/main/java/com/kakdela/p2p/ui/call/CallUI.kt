package com.kakdela.p2p.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.getstream.webrtc.android.compose.VideoRenderer
import org.webrtc.VideoTrack

@Composable
fun CallUI(
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    onHangup: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        remoteTrack?.let {
            VideoRenderer(
                modifier = Modifier.fillMaxSize(),
                videoTrack = it
            )
        }

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            localTrack?.let {
                VideoRenderer(
                    modifier = Modifier
                        .size(160.dp)
                        .padding(8.dp),
                    videoTrack = it
                )
            }

            Button(
                onClick = onHangup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Завершить")
            }
        }
    }
}
