package com.kakdela.p2p.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val currentUserId: String,
    private val onRemoteStreamReady: (MediaStream) -> Unit
) {
    private val db = FirebaseFirestore.getInstance()
    private val rootEglBase: EglBase = EglBase.create()
    
    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startLocalVideoCapture(surface: SurfaceViewRenderer) {
        surface.init(rootEglBase.eglBaseContext, null)
        surface.setMirror(true)

        val videoCapturer = createCameraCapturer(Camera2Enumerator(context)) ?: return
        val videoSource = factory?.createVideoSource(videoCapturer.isScreencast)
        
        videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, videoSource?.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        localVideoTrack = factory?.createVideoTrack("100", videoSource)
        localVideoTrack?.addSink(surface)

        val audioSource = factory?.createAudioSource(MediaConstraints())
        localAudioTrack = factory?.createAudioTrack("101", audioSource)
    }

    fun initCall(targetUserId: String) {
        createPeerConnection(targetUserId)
        doCall(targetUserId)
    }

    fun answerCall(targetUserId: String, offerSdp: String) {
        createPeerConnection(targetUserId)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        doAnswer(targetUserId)
    }

    private fun createPeerConnection(targetUserId: String) {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
        )

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                sendIceCandidate(candidate, targetUserId)
            }
            override fun onAddStream(stream: MediaStream) {
                onRemoteStreamReady(stream)
            }
            // Остальные методы заглушки...
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onDataChannel(p0: DataChannel?) {}
        })

        // Добавляем локальные стримы
        val stream = factory?.createLocalMediaStream("ARDAMS")
        stream?.addTrack(localVideoTrack)
        stream?.addTrack(localAudioTrack)
        peerConnection?.addStream(stream)
    }

    private fun doCall(targetUserId: String) {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                // Отправляем Offer через Firestore (сигнализация)
                sendSignalingMessage(targetUserId, "offer", desc?.description)
            }
        }, MediaConstraints())
    }

    private fun doAnswer(targetUserId: String) {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                sendSignalingMessage(targetUserId, "answer", desc?.description)
            }
        }, MediaConstraints())
    }
    
    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }
    
    fun setRemoteDescription(sdp: String) {
         peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun sendSignalingMessage(targetId: String, type: String, sdp: String?) {
        val data = mapOf(
            "type" to type,
            "sdp" to sdp,
            "senderId" to currentUserId,
            "timestamp" to System.currentTimeMillis()
        )
        // В реальном приложении это отправляется в sub-collection 'calls'
        db.collection("users").document(targetId).collection("incoming_calls").add(data)
    }
    
    private fun sendIceCandidate(candidate: IceCandidate, targetId: String) {
         val data = mapOf(
            "type" to "candidate",
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "senderId" to currentUserId
        )
        db.collection("users").document(targetId).collection("incoming_calls").add(data)
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Ищем переднюю камеру
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) return videoCapturer
            }
        }
        return null
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
