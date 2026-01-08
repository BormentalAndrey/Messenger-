package com.kakdela.p2p.ui.call

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.kakdela.p2p.MyApplication
import com.kakdela.p2p.data.IdentityRepository
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList

class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
    }

    private lateinit var identityRepo: IdentityRepository
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val eglBase = EglBase.create()

    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var localVideoTrack by mutableStateOf<VideoTrack?>(null)

    private var targetIp: String = ""
    private var isIncoming = false
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    @Volatile private var isRemoteSdpSet = false

    // Исправлено: корректный return для лямбды
    private val signalingListener: (String, String, String) -> Unit = { type, data, fromIp ->
        if (fromIp == targetIp) {
            when (type) {
                "ANSWER" -> handleAnswer(data)
                "ICE" -> handleRemoteIce(data)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация репозитория
        val app = application as? MyApplication
        if (app == null) {
            finish()
            return
        }
        identityRepo = app.identityRepository

        targetIp = intent.getStringExtra("targetIp") ?: ""
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        val remoteSdp = intent.getStringExtra("remoteSdp")

        identityRepo.addListener(signalingListener)

        initWebRTC()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                setupLocalStream()
                if (isIncoming && remoteSdp != null) {
                    handleIncomingOffer(remoteSdp)
                } else {
                    makeOffer()
                }
            } else {
                Log.e(TAG, "Permissions denied for WebRTC")
                finish()
            }
        }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA, 
            Manifest.permission.RECORD_AUDIO
        ))

        setContent {
            // Исправлено: передаем контекст eglBase, как того требует UI компонент
            CallUI(
                localTrack = localVideoTrack,
                remoteTrack = remoteVideoTrack,
                eglBaseContext = eglBase.eglBaseContext,
                onHangup = { finish() }
            )
        }
    }

    override fun onDestroy() {
        identityRepo.removeListener(signalingListener)
        peerConnection?.dispose() // Правильнее использовать dispose для очистки нативных ресурсов
        peerConnection = null
        factory.dispose()
        eglBase.release()
        super.onDestroy()
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                identityRepo.sendSignaling(targetIp, "ICE", payload)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.DISCONNECTED) finish()
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    private fun setupLocalStream() {
        val videoSource = factory.createVideoSource(false)
        val surfaceHelper = SurfaceTextureHelper.create("WebRTC", eglBase.eglBaseContext)
        val enumerator = Camera2Enumerator(this)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } 
            ?: enumerator.deviceNames.firstOrNull() ?: return
            
        val capturer = enumerator.createCapturer(cameraName, null)
        capturer.initialize(surfaceHelper, this, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("LOCAL_VIDEO", videoSource)
        peerConnection?.addTrack(localVideoTrack)
        
        val audioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("AUDIO", audioSource)
        peerConnection?.addTrack(audioTrack)
    }

    private fun handleIncomingOffer(sdpStr: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        peerConnection?.setRemoteDescription(SdpAdapter {
            isRemoteSdpSet = true
            drainIce()
            peerConnection?.createAnswer(SdpAdapter { answer ->
                peerConnection?.setLocalDescription(SdpAdapter(), answer)
                identityRepo.sendSignaling(targetIp, "ANSWER", answer.description)
            }, MediaConstraints())
        }, offer)
    }

    private fun makeOffer() {
        peerConnection?.createOffer(SdpAdapter { offer ->
            peerConnection?.setLocalDescription(SdpAdapter(), offer)
            identityRepo.sendSignaling(targetIp, "OFFER", offer.description)
        }, MediaConstraints())
    }

    private fun handleAnswer(sdpStr: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
        peerConnection?.setRemoteDescription(SdpAdapter {
            isRemoteSdpSet = true
            drainIce()
        }, answer)
    }

    private fun handleRemoteIce(data: String) {
        val parts = data.split("|")
        if (parts.size < 3) return
        val candidate = IceCandidate(parts[0], parts[1].toInt(), parts[2])
        if (isRemoteSdpSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIce.add(candidate)
        }
    }

    private fun drainIce() {
        pendingIce.forEach { peerConnection?.addIceCandidate(it) }
        pendingIce.clear()
    }

    private open class SdpAdapter(val onSuccess: (SessionDescription) -> Unit = {}) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) { desc?.let(onSuccess) }
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e(TAG, "SDP create error: $p0") }
        override fun onSetFailure(p0: String?) { Log.e(TAG, "SDP set error: $p0") }
    }
}
