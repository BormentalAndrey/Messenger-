package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val identityRepository: IdentityRepository,
    private val currentUserHash: String,
    private val onRemoteStreamReady: (MediaStream) -> Unit
) {

    private val TAG = "WebRtcManager"

    private val rootEglBase = EglBase.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()

        identityRepository.addListener(::onSignalingPacket)
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        surface.init(rootEglBase.eglBaseContext, null)
        surface.setMirror(true)

        val capturer = createCameraCapturer(Camera2Enumerator(context)) ?: return
        val videoSource = factory.createVideoSource(capturer.isScreencast)

        capturer.initialize(
            SurfaceTextureHelper.create("VideoCapture", rootEglBase.eglBaseContext),
            context,
            videoSource.capturerObserver
        )
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("VIDEO", videoSource)
        localVideoTrack?.addSink(surface)

        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("AUDIO", audioSource)
    }

    fun call(targetHash: String) {
        createPeer(targetHash)
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                sendSignal(targetHash, "OFFER", desc.description)
            }
        }, MediaConstraints())
    }

    fun answer(targetHash: String, offerSdp: String) {
        createPeer(targetHash)
        peerConnection?.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        )

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                sendSignal(targetHash, "ANSWER", desc.description)
            }
        }, MediaConstraints())
    }

    private fun createPeer(targetHash: String) {
        if (peerConnection != null) return

        val config = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )

        peerConnection = factory.createPeerConnection(
            config,
            object : PeerConnection.Observer {
                override fun onIceCandidate(c: IceCandidate) {
                    sendSignal(targetHash, "ICE", JSONObject().apply {
                        put("sdpMid", c.sdpMid)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("candidate", c.sdp)
                    }.toString())
                }

                override fun onAddStream(stream: MediaStream) {
                    onRemoteStreamReady(stream)
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onTrack(transceiver: RtpTransceiver?) {}
            }
        )

        val stream = factory.createLocalMediaStream("LOCAL")
        localVideoTrack?.let { stream.addTrack(it) }
        localAudioTrack?.let { stream.addTrack(it) }
        peerConnection?.addStream(stream)
    }

    private fun sendSignal(targetHash: String, type: String, payload: String) {
        scope.launch {
            val json = JSONObject().apply {
                put("type", type)
                put("payload", payload)
            }
            identityRepository.sendSignaling(targetHash, json.toString())
        }
    }

    private fun onSignalingPacket(type: String, data: String, _: String, fromHash: String) {
        if (type != "SIGNALING") return

        try {
            val json = JSONObject(data)
            when (json.getString("type")) {
                "OFFER" -> answer(fromHash, json.getString("payload"))
                "ANSWER" -> peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, json.getString("payload"))
                )
                "ICE" -> {
                    val p = JSONObject(json.getString("payload"))
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            p.getString("sdpMid"),
                            p.getInt("sdpMLineIndex"),
                            p.getString("candidate")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signal parse error", e)
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? =
        enumerator.deviceNames.firstNotNullOfOrNull {
            if (enumerator.isFrontFacing(it)) enumerator.createCapturer(it, null) else null
        }

    fun release() {
        identityRepository.removeListener(::onSignalingPacket)
        peerConnection?.close()
        peerConnection = null
        scope.cancel()
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
