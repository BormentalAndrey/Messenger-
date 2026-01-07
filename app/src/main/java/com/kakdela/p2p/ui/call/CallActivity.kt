package com.kakdela.p2p.ui.call

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.IdentityRepository
import io.getstream.webrtc.android.compose.VideoRenderer
import org.webrtc.*

class CallActivity : ComponentActivity() {

    // WebRTC компоненты
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private var localVideoTrack: VideoTrack? by mutableStateOf(null)
    private var remoteVideoTrack: VideoTrack? by mutableStateOf(null)
    
    private lateinit var videoCapturer: CameraVideoCapturer
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetIp = intent.getStringExtra("targetIp") ?: ""
        val isIncoming = intent.getBooleanExtra("isIncoming", false)

        initWebRTC()

        setContent {
            var isMuted by remember { mutableStateOf(false) }
            var isCameraOn by remember { mutableStateOf(true) }

            // Запрос разрешений перед началом
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.all { it.value }) {
                    startLocalStream()
                    if (isIncoming) answerCall(targetIp) else startCall(targetIp)
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Удаленное видео
                remoteVideoTrack?.let {
                    VideoRenderer(videoTrack = it, modifier = Modifier.fillMaxSize())
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Установка прямого соединения...", color = Color.Cyan)
                }

                // Локальное видео
                if (isCameraOn) {
                    localVideoTrack?.let {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(120.dp, 180.dp)
                            .clip(MaterialTheme.shapes.medium).background(Color.DarkGray)) {
                            VideoRenderer(videoTrack = it, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                // Управление
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    
                    FloatingActionButton(onClick = { isMuted = !isMuted }, containerColor = if (isMuted) Color.Red else Color.DarkGray) {
                        Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
                    }

                    FloatingActionButton(onClick = { endCall() }, containerColor = Color.Red, modifier = Modifier.size(70.dp)) {
                        Icon(Icons.Default.CallEnd, null, tint = Color.White)
                    }

                    FloatingActionButton(onClick = { isCameraOn = !isCameraOn }, containerColor = if (!isCameraOn) Color.Red else Color.DarkGray) {
                        Icon(if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, null, tint = Color.White)
                    }
                }
            }
        }
    }

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        
        val options = PeerConnectionFactory.Options()
        val videoEncoderFactory = DefaultVideoEncoderFactory(DefaultVideoCodecInfoFactory.createDefaultVideoCodecInfo())
        val videoDecoderFactory = DefaultVideoDecoderFactory(DefaultVideoCodecInfoFactory.createDefaultVideoCodecInfo())

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // ВАЖНО: Отправляем наш ICE-candidate собеседнику через UDP (IdentityRepository)
                sendSignalingData("CANDIDATE", candidate.sdp)
            }
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) { remoteVideoTrack = track }
            }
            // Остальные методы observer...
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })!!
    }

    private fun startLocalStream() {
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", null)
        videoCapturer = Camera2Enumerator(this).let { enumerator ->
            enumerator.deviceNames.find { enumerator.isFrontFacing(it) }?.let {
                enumerator.createCapturer(it, null)
            } ?: throw Exception("Камера не найдена")
        }

        val videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("101", videoSource)
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val localAudioTrack = peerConnectionFactory.createAudioTrack("102", audioSource)

        peerConnection.addTrack(localVideoTrack)
        peerConnection.addTrack(localAudioTrack)
    }

    private fun startCall(targetIp: String) {
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                sendSignalingData("OFFER", sdp.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun answerCall(targetIp: String) {
        // Логика аналогична startCall, но вызывается createAnswer
    }

    private fun sendSignalingData(type: String, data: String) {
        // Здесь используется ваш IdentityRepository для отправки UDP пакета
        // Например: identityRepo.sendRawUdp(targetIp, json(type, data))
    }

    private fun endCall() {
        videoCapturer.stopCapture()
        peerConnection.close()
        finish()
    }
}

