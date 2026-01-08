package com.kakdela.p2p.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoTrackView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                track.addSink(this)
            }
        },
        modifier = modifier
    )
}

@Composable
fun CallUI(
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    onHangup: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        VideoTrackView(
            track = remoteTrack,
            eglBaseContext = eglBaseContext,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            VideoTrackView(
                track = localTrack,
                eglBaseContext = eglBaseContext,
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            )

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
