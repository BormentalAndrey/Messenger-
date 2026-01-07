package com.kakdela.p2p.ui.call

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.IdentityRepository
import io.getstream.webrtc.android.compose.VideoRenderer
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CallActivity реализует логику видеозвонка точка-точка (P2P).
 * Использует WebRTC для передачи медиаданных и IdentityRepository для обмена сигналами (SDP/ICE).
 */
class CallActivity : ComponentActivity() {

    private lateinit var identityRepo: IdentityRepository
    private lateinit var factory: PeerConnectionFactory
    private lateinit var peer: PeerConnection

    private val eglBase = EglBase.create()

    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)

    // Очередь для ICE-кандидатов, пришедших до того, как удаленный SDP был обработан
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()

    private var targetIp = ""
    private var isIncoming = false
    private var remoteOffer: String? = null
    private var remoteSdpSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identityRepo = IdentityRepository(this)

        targetIp = intent.getStringExtra("targetIp") ?: ""
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        remoteOffer = intent.getStringExtra("remoteSdp")

        initWebRTC()
        bindSignaling()

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                if (granted.all { it.value }) {
                    startLocalMedia()
                    if (isIncoming && !remoteOffer.isNullOrEmpty()) {
                        answerCall(remoteOffer!!)
                    } else {
                        startCall()
                    }
                } else {
                    Log.e("CallActivity", "Permissions not granted")
                    finish()
                }
            }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )

        setContent { CallUI() }
    }

    /* ----------------------------------------------------
       UI Элементы
     ---------------------------------------------------- */

    @Composable
    private fun CallUI() {
        var muted by remember { mutableStateOf(false) }
        var cameraOn by remember { mutableStateOf(true) }

        Box(Modifier.fillMaxSize().background(Color.Black)) {

            

            remoteVideoTrack?.let {
                VideoRenderer(
                    videoTrack = it,
                    eglBaseContext = eglBase.eglBaseContext,
                    modifier = Modifier.fillMaxSize(),
                    rendererEvents = object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {}
                        override fun onFrameResolutionChanged(w: Int, h: Int, rot: Int) {}
                    }
                )
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Cyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Установка P2P соединения…", color = Color.Cyan)
                }
            }

            if (cameraOn) {
                localVideoTrack?.let {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(120.dp, 180.dp)
                    ) {
                        VideoRenderer(
                            videoTrack = it,
                            eglBaseContext = eglBase.eglBaseContext,
                            modifier = Modifier.fillMaxSize(),
                            rendererEvents = object : RendererCommon.RendererEvents {
                                override fun onFirstFrameRendered() {}
                                override fun onFrameResolutionChanged(w: Int, h: Int, rot: Int) {}
                            }
                        )
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        muted = !muted
                        peer.senders.mapNotNull { it.track() as? AudioTrack }
                            .forEach { it.setEnabled(!muted) }
                    },
                    containerColor = if (muted) Color.Red else Color.DarkGray
                ) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
                }

                FloatingActionButton(
                    onClick = { finish() },
                    containerColor = Color.Red,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White)
                }

                FloatingActionButton(
                    onClick = {
                        cameraOn = !cameraOn
                        localVideoTrack?.setEnabled(cameraOn)
                    },
                    containerColor = if (!cameraOn) Color.Red else Color.DarkGray
                ) {
                    Icon(
                        if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        null, tint = Color.White
                    )
                }
            }
        }
    }

    /* ----------------------------------------------------
       WebRTC Логика
     ---------------------------------------------------- */

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        val config = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // Отправляем найденного ICE-кандидата через наш P2P репозиторий
                identityRepo.sendSignaling(
                    targetIp,
                    "ICE",
                    "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                )
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                if (stream?.videoTracks?.isNotEmpty() == true) {
                    remoteVideoTrack = stream.videoTracks[0]
                }
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                if (state == PeerConnection.PeerConnectionState.DISCONNECTED || 
                    state == PeerConnection.PeerConnectionState.FAILED) {
                    finish()
                }
            }

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

    private fun startLocalMedia() {
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val enumerator = Camera2Enumerator(this)
        val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return

        capturer = enumerator.createCapturer(device, null)
        val videoSource = factory.createVideoSource(false)
        capturer?.initialize(surfaceHelper, this, videoSource.capturerObserver)
        capturer?.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("LOCAL_VIDEO", videoSource)
        val audioTrack = factory.createAudioTrack("LOCAL_AUDIO", factory.createAudioSource(MediaConstraints()))

        peer.addTrack(localVideoTrack)
        peer.addTrack(audioTrack)
    }

    /* ----------------------------------------------------
       P2P Сигналинг
     ---------------------------------------------------- */

    private fun bindSignaling() {
        

        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (fromIp == targetIp) {
                when (type) {
                    "ANSWER" -> {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, data)
                        peer.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                remoteSdpSet = true
                                flushIce()
                            }
                        }, sdp)
                    }
                    "ICE" -> {
                        val parts = data.split("|", limit = 3)
                        if (parts.size == 3) {
                            val ice = IceCandidate(parts[0], parts[1].toInt(), parts[2])
                            if (remoteSdpSet) peer.addIceCandidate(ice)
                            else pendingIce.add(ice)
                        }
                    }
                }
            }
        }
    }

    private fun flushIce() {
        pendingIce.forEach { peer.addIceCandidate(it) }
        pendingIce.clear()
    }

    private fun startCall() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peer.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peer.setLocalDescription(object : SimpleSdpObserver() {}, it)
                    identityRepo.sendSignaling(targetIp, "OFFER", it.description)
                }
            }
        }, constraints)
    }

    private fun answerCall(offerDescription: String) {
        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerDescription)
        peer.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteSdpSet = true
                peer.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp?.let {
                            peer.setLocalDescription(object : SimpleSdpObserver() {}, it)
                            identityRepo.sendSignaling(targetIp, "ANSWER", it.description)
                            flushIce()
                        }
                    }
                }, MediaConstraints())
            }
        }, offerSdp)
    }

    override fun onDestroy() {
        try {
            capturer?.stopCapture()
            capturer?.dispose()
            surfaceHelper?.dispose()
            peer.close()
            factory.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.e("CallActivity", "Cleanup error: ${e.message}")
        }
        super.onDestroy()
    }

    /**
     * Базовая реализация наблюдателя SDP событий с исправленными сигнатурами для Kotlin.
     */
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e("WebRTC", "SDP Create Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e("WebRTC", "SDP Set Failure: $error") }
    }
}

