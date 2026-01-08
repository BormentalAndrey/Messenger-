package com.kakdela.p2p.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.getstream.webrtc.android.compose.VideoRenderer
import org.webrtc.EglBase
import org.webrtc.VideoTrack

@Composable
fun CallUI(
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    rendererEvents: VideoRenderer.Callbacks,
    onHangup: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        remoteTrack?.let {
            VideoRenderer(
                modifier = Modifier.fillMaxSize(),
                videoTrack = it,
                eglBaseContext = eglBaseContext,
                rendererEvents = rendererEvents
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            localTrack?.let {
                VideoRenderer(
                    modifier = Modifier
                        .size(160.dp)
                        .padding(8.dp),
                    videoTrack = it,
                    eglBaseContext = eglBaseContext,
                    rendererEvents = rendererEvents
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
