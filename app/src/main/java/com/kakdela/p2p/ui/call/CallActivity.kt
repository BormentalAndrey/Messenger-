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
import io.getstream.webrtc.android.compose.VideoRenderer
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList

class CallActivity : ComponentActivity() {

    private lateinit var identityRepo: IdentityRepository
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val eglBase = EglBase.create()

    // Состояние для UI
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var localVideoTrack by mutableStateOf<VideoTrack?>(null)

    private var targetIp: String = ""
    private var isIncoming: Boolean = false
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    private var isRemoteDescriptionSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Получаем глобальный репозиторий из Application
        identityRepo = (application as MyApplication).identityRepository

        targetIp = intent.getStringExtra("targetIp") ?: ""
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        val remoteSdp = intent.getStringExtra("remoteSdp")

        initWebRTC()

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.all { entry -> entry.value }) {
                setupLocalStream()
                if (isIncoming && remoteSdp != null) {
                    handleIncomingOffer(remoteSdp)
                } else {
                    makeOffer()
                }
            } else {
                finish()
            }
        }
        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))

        setContent {
            CallUI(
                localTrack = localVideoTrack,
                remoteTrack = remoteVideoTrack,
                onHangup = { finish() }
            )
        }
    }

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                identityRepo.sendSignaling(targetIp, "ICE", "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}")
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                if (transceiver?.receiver?.track() is VideoTrack) {
                    remoteVideoTrack = transceiver.receiver.track() as VideoTrack
                }
            }
            // Другие методы пустые...
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        // Слушаем ответные сигналы
        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (fromIp == targetIp) {
                when (type) {
                    "ANSWER" -> {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, data)
                        peerConnection?.setRemoteDescription(SdpAdapter {
                            isRemoteDescriptionSet = true
                            pendingIce.forEach { peerConnection?.addIceCandidate(it) }
                            pendingIce.clear()
                        }, sdp)
                    }
                    "ICE" -> {
                        val p = data.split("|")
                        val candidate = IceCandidate(p[0], p[1].toInt(), p[2])
                        if (isRemoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
                        else pendingIce.add(candidate)
                    }
                }
            }
        }
    }

    private fun setupLocalStream() {
        val videoSource = factory.createVideoSource(false)
        val surfaceHelper = SurfaceTextureHelper.create("WebRTCThread", eglBase.eglBaseContext)
        val capturer = Camera2Enumerator(this).let { 
            it.createCapturer(it.deviceNames.first { name -> it.isFrontFacing(name) }, null) 
        }
        capturer.initialize(surfaceHelper, this, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        
        localVideoTrack = factory.createVideoTrack("VIDEO", videoSource)
        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(factory.createAudioTrack("AUDIO", factory.createAudioSource(MediaConstraints())))
    }

    private fun handleIncomingOffer(sdpStr: String) {
        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        peerConnection?.setRemoteDescription(SdpAdapter {
            isRemoteDescriptionSet = true
            peerConnection?.createAnswer(SdpAdapter { answer ->
                peerConnection?.setLocalDescription(SdpAdapter {}, answer)
                identityRepo.sendSignaling(targetIp, "ANSWER", answer.description)
            }, MediaConstraints())
        }, sdp)
    }

    private fun makeOffer() {
        peerConnection?.createOffer(SdpAdapter { offer ->
            peerConnection?.setLocalDescription(SdpAdapter {}, offer)
            identityRepo.sendSignaling(targetIp, "OFFER", offer.description)
        }, MediaConstraints())
    }

    override fun onDestroy() {
        peerConnection?.close()
        factory.dispose()
        eglBase.release()
        super.onDestroy()
    }

    // Вспомогательный класс, чтобы не писать пустые методы
    internal class SdpAdapter(val done: (SessionDescription) -> Unit = {}) : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) { p0?.let(done) }
        override fun onSetSuccess() { /* Отработает через onCreate для Answer */ }
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
