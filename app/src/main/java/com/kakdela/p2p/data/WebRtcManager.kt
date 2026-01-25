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
        // Инициализация PeerConnectionFactory
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
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

        // Подписка на входящие сигнализации
        identityRepository.addListener(::onSignalingPacket)
    }

    /** Запуск локального видео */
    fun startLocalVideo(surface: SurfaceViewRenderer) {
        surface.init(rootEglBase.eglBaseContext, null)
        surface.setMirror(true)

        val capturer = createCameraCapturer(Camera2Enumerator(context)) ?: run {
            Log.w(TAG, "No front-facing camera found")
            return
        }

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

    /** Начало звонка */
    fun call(targetHash: String) {
        createPeerConnection(targetHash)
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                sendSignal(targetHash, "OFFER", desc.description)
            }
        }, MediaConstraints())
    }

    /** Ответ на входящий звонок */
    fun answer(targetHash: String, offerSdp: String) {
        createPeerConnection(targetHash)
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

    /** Создание PeerConnection и добавление локальных потоков */
    private fun createPeerConnection(targetHash: String) {
        if (peerConnection != null) return

        val config = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )
        
        // Важно для корректной работы в современных сетях
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        config.continuationTimeoutMs = 30000

        peerConnection = factory.createPeerConnection(
            config,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    sendSignal(targetHash, "ICE", JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    }.toString())
                }

                // Исправление ошибки: Реализация обязательного метода onAddTrack
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    streams?.firstOrNull()?.let {
                        Log.d(TAG, "onAddTrack: Remote stream found")
                        onRemoteStreamReady(it)
                    }
                }

                override fun onAddStream(stream: MediaStream) {
                    Log.d(TAG, "onAddStream: Legacy stream added")
                    onRemoteStreamReady(stream)
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "SignalingState: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "IceConnectionState: $state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.d(TAG, "onTrack: New transceiver received")
                }
            }
        )

        // Добавление локальных потоков
        val localStream = factory.createLocalMediaStream("LOCAL")
        localVideoTrack?.let { localStream.addTrack(it) }
        localAudioTrack?.let { localStream.addTrack(it) }
        peerConnection?.addStream(localStream)
    }

    /** Отправка сигнализации через IdentityRepository */
    private fun sendSignal(targetHash: String, type: String, payload: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", type)
                    put("payload", payload)
                }
                identityRepository.sendSignaling(targetHash, json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error sending signal", e)
            }
        }
    }

    /** Обработка входящей сигнализации */
    private fun onSignalingPacket(type: String, data: String, ignored: String, fromHash: String) {
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

    /** Создание VideoCapturer для фронтальной камеры */
    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    /** Очистка ресурсов */
    fun release() {
        identityRepository.removeListener(::onSignalingPacket)
        peerConnection?.dispose() // В проде лучше использовать dispose()
        peerConnection = null
        localVideoTrack = null
        localAudioTrack = null
        factory.dispose()
        rootEglBase.release()
        scope.cancel()
    }
}

/** Простая реализация SdpObserver */
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("SdpObserver", "onCreateFailure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("SdpObserver", "onSetFailure: $error")
    }
}
