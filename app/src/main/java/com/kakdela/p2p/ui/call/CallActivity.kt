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

    /* ===================== UI STATE ===================== */
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var localVideoTrack by mutableStateOf<VideoTrack?>(null)

    /* ===================== CALL STATE ===================== */
    private var targetIp: String = ""
    private var isIncoming = false

    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    @Volatile
    private var isRemoteSdpSet = false

    /* ===================== SIGNAL LISTENER ===================== */
    private val signalingListener: (String, String, String) -> Unit =
        { type, data, fromIp ->
            if (fromIp != targetIp) return@signalingListener

            when (type) {
                "ANSWER" -> handleAnswer(data)
                "ICE" -> handleRemoteIce(data)
            }
        }

    /* ===================== LIFECYCLE ===================== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identityRepo = (application as MyApplication).identityRepository

        targetIp = intent.getStringExtra("targetIp") ?: ""
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        val remoteSdp = intent.getStringExtra("remoteSdp")

        identityRepo.addListener(signalingListener)

        initWebRTC()

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it.all { p -> p.value }) {
                    setupLocalStream()

                    remoteSdp?.let {
                        if (isIncoming) handleIncomingOffer(it)
                    } ?: run {
                        makeOffer()
                    }
                } else {
                    finish()
                }
            }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )

        setContent {
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

        peerConnection?.close()
        peerConnection = null

        factory.dispose()
        eglBase.release()

        super.onDestroy()
    }

    /* ===================== WEBRTC INIT ===================== */
    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer
                    .builder("stun:stun.l.google.com:19302")
                    .createIceServer()
            )
        )

        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {

                override fun onIceCandidate(candidate: IceCandidate) {
                    val payload =
                        "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                    identityRepo.sendSignaling(targetIp, "ICE", payload)
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                    }
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            }
        )
    }

    /* ===================== LOCAL MEDIA ===================== */
    private fun setupLocalStream() {
        val videoSource = factory.createVideoSource(false)
        val surfaceHelper =
            SurfaceTextureHelper.create("WebRTC", eglBase.eglBaseContext)

        val enumerator = Camera2Enumerator(this)
        val cameraName =
            enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: return

        val capturer = enumerator.createCapturer(cameraName, null)
        capturer.initialize(surfaceHelper, this, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("LOCAL_VIDEO", videoSource)

        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(
            factory.createAudioTrack(
                "AUDIO",
                factory.createAudioSource(MediaConstraints())
            )
        )
    }

    /* ===================== SIGNALING HANDLERS ===================== */
    private fun handleIncomingOffer(sdpStr: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)

        peerConnection?.setRemoteDescription(
            SdpAdapter {
                isRemoteSdpSet = true
                drainIce()

                peerConnection?.createAnswer(
                    SdpAdapter { answer ->
                        peerConnection?.setLocalDescription(SdpAdapter(), answer)
                        identityRepo.sendSignaling(
                            targetIp,
                            "ANSWER",
                            answer.description
                        )
                    },
                    MediaConstraints()
                )
            },
            offer
        )
    }

    private fun makeOffer() {
        peerConnection?.createOffer(
            SdpAdapter { offer ->
                peerConnection?.setLocalDescription(SdpAdapter(), offer)
                identityRepo.sendSignaling(
                    targetIp,
                    "OFFER",
                    offer.description
                )
            },
            MediaConstraints()
        )
    }

    private fun handleAnswer(sdpStr: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)

        peerConnection?.setRemoteDescription(
            SdpAdapter {
                isRemoteSdpSet = true
                drainIce()
            },
            answer
        )
    }

    private fun handleRemoteIce(data: String) {
        val parts = data.split("|")
        if (parts.size != 3) return

        val candidate = IceCandidate(
            parts[0],
            parts[1].toInt(),
            parts[2]
        )

        if (isRemoteSdpSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIce += candidate
        }
    }

    private fun drainIce() {
        pendingIce.forEach { peerConnection?.addIceCandidate(it) }
        pendingIce.clear()
    }

    /* ===================== SDP ADAPTER ===================== */
    private class SdpAdapter(
        private val onSuccess: (SessionDescription) -> Unit = {}
    ) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {
            desc?.let(onSuccess)
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {
            Log.e(TAG, "SDP create error: $p0")
        }

        override fun onSetFailure(p0: String?) {
            Log.e(TAG, "SDP set error: $p0")
        }
    }
}
