package com.kakdela.p2p.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoTrackView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    // Используем DisposableEffect для правильного управления жизненным циклом Renderer
    DisposableEffect(track) {
        onDispose {
            // Здесь можно добавить логику отписки, если требуется
        }
    }

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // Инициализация графического движка
                init(eglBaseContext, null)
                // Настройка масштабирования (заполнение экрана без искажений)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true) // Для фронтальной камеры обычно включают зеркалирование
                track?.addSink(this)
            }
        },
        modifier = modifier,
        update = { view ->
            // Если трек поменялся, переподключаем его
            track?.addSink(view)
        },
        onRelease = { view ->
            // ОБЯЗАТЕЛЬНО: освобождаем ресурсы при удалении View из Compose
            view.release()
        }
    )
}

@Composable
fun CallUI(
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    onHangup: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        // Удаленное видео (на весь экран)
        if (remoteTrack != null) {
            VideoTrackView(
                track = remoteTrack,
                eglBaseContext = eglBaseContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Заглушка, пока ждем подключения
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Интерфейс управления
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            // Локальное видео (маленькое окошко в углу)
            if (localTrack != null) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(width = 120.dp, height = 180.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    VideoTrackView(
                        track = localTrack,
                        eglBaseContext = eglBaseContext,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка отбоя
            Button(
                onClick = onHangup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Завершить вызов", color = Color.White)
            }
        }
    }
}
