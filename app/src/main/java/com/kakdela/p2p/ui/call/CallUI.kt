package com.kakdela.p2p.ui.call

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val NeonCyan = Color(0xFF00FFFF)
private val NeonGreen = Color(0xFF00FF9D)
private val NeonRed = Color(0xFFFF3131)
private val DarkBg = Color(0xFF0A0A0A)

@Composable
fun CallUI(
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    chatPartnerName: String,
    isIncoming: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onToggleVideo: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onAddUser: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(localTrack != null) }
    var isRecording by remember { mutableStateOf(false) }
    var callAccepted by remember { mutableStateOf(!isIncoming) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        
        // 1. УДАЛЕННОЕ ВИДЕО (Задний план)
        if (callAccepted && remoteTrack != null) {
            VideoTrackView(remoteTrack, eglBaseContext, Modifier.fillMaxSize())
        } else {
            // Заставка ожидания или входящего вызова
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(DarkBg, Color(0xFF1A1A1A)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        color = Color.DarkGray,
                        border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan)
                    ) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.LightGray)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(chatPartnerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isIncoming && !callAccepted) "Входящий P2P вызов..." else "Соединение...",
                        color = NeonGreen,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // 2. ЛОКАЛЬНОЕ ВИДЕО (Маленькое окно)
        if (callAccepted && localTrack != null && isVideoEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(120.dp, 180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
            ) {
                VideoTrackView(localTrack, eglBaseContext, Modifier.fillMaxSize())
            }
        }

        // 3. ПАНЕЛЬ УПРАВЛЕНИЯ
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isIncoming && !callAccepted) {
                // Кнопки для входящего вызова
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallActionButton(Icons.Default.Call, "Принять", NeonGreen) {
                        callAccepted = true
                        onAccept()
                    }
                    CallActionButton(Icons.Default.CallEnd, "Отклонить", NeonRed) {
                        onReject()
                    }
                }
            } else {
                // Функциональные кнопки активного вызова
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlIconButton(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, isMuted) {
                        isMuted = !isMuted
                        onToggleMute(isMuted)
                    }
                    ControlIconButton(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown, isSpeakerOn) {
                        isSpeakerOn = !isSpeakerOn
                        onToggleSpeaker(isSpeakerOn)
                    }
                    ControlIconButton(if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, !isVideoEnabled) {
                        isVideoEnabled = !isVideoEnabled
                        onToggleVideo(isVideoEnabled)
                    }
                    ControlIconButton(Icons.Default.FiberManualRecord, isRecording, if (isRecording) NeonRed else Color.White) {
                        isRecording = !isRecording
                        onStartRecording()
                    }
                    ControlIconButton(Icons.Default.PersonAdd, false) {
                        onAddUser()
                    }
                }
                
                Spacer(Modifier.height(40.dp))

                // Кнопка сброса (Центральная)
                FloatingActionButton(
                    onClick = onHangup,
                    containerColor = NeonRed,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun ControlIconButton(icon: ImageVector, isActive: Boolean, activeColor: Color = NeonCyan, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, if (isActive) activeColor else Color.Transparent, CircleShape)
    ) {
        Icon(icon, null, tint = if (isActive) activeColor else Color.White)
    }
}

@Composable
fun CallActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = color,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun VideoTrackView(track: VideoTrack, eglBaseContext: EglBase.Context, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true)
                track.addSink(this)
            }
        },
        modifier = modifier,
        onRelease = { it.release() }
    )
}
