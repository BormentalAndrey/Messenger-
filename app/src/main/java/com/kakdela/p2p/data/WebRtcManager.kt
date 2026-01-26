package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Менеджер WebRTC для управления P2P соединениями.
 * Реализует логику захвата медиа, обмена SDP и ICE-кандидатами через IdentityRepository.
 */
class WebRtcManager(
    private val context: Context,
    private val identityRepository: IdentityRepository,
    private val onRemoteStreamReady: (MediaStream) -> Unit
) {

    private val TAG = "WebRtcManager"

    private val rootEglBase = EglBase.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var remoteMediaStream: MediaStream? = null

    private val pendingIceCandidates = CopyOnWriteArrayList<IceCandidate>()
    @Volatile private var isRemoteDescriptionSet = false

    // Ссылка на слушатель для корректного удаления в release()
    private val signalingListener: (String, String, String, String) -> Unit = { type, data, _, fromHash ->
        onSignalingPacket(type, data, fromHash)
    }

    init {
        initPeerConnectionFactory()
        identityRepository.addListener(signalingListener)
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /** Запуск локального видео-превью */
    fun startLocalVideo(surface: SurfaceViewRenderer) {
        try {
            surface.init(rootEglBase.eglBaseContext, null)
            surface.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            surface.setEnableHardwareScaler(true)

            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames

            val frontDevice = deviceNames.find { enumerator.isFrontFacing(it) }
            val selectedDevice = frontDevice ?: deviceNames.firstOrNull()

            if (selectedDevice == null) {
                Log.e(TAG, "Камера не найдена")
                return
            }

            videoCapturer = enumerator.createCapturer(selectedDevice, null)
            surface.setMirror(frontDevice != null)

            surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapture", rootEglBase.eglBaseContext)

            val videoSource = factory.createVideoSource(false)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
            localVideoTrack?.addSink(surface)

            val audioSource = factory.createAudioSource(MediaConstraints())
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска локального видео: ${e.message}")
        }
    }

    /** Инициация исходящего вызова */
    fun call(targetHash: String) {
        createPeerConnection(targetHash)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                sendSignal(targetHash, "OFFER", desc.description)
            }
        }, constraints)
    }

    /** Ответ на входящий вызов */
    fun answer(targetHash: String, offerSdp: String) {
        createPeerConnection(targetHash)
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainPendingIceCandidates()
                
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                        sendSignal(targetHash, "ANSWER", desc.description)
                    }
                }, constraints)
            }
        }, offer)
    }

    /** Создание и настройка PeerConnection (Unified Plan) */
    private fun createPeerConnection(targetHash: String) {
        if (peerConnection != null) return

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = "${candidate.sdpMid ?: ""}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                sendSignal(targetHash, "ICE", payload)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                Log.d(TAG, "Получен новый трек: ${track.kind()}")

                if (remoteMediaStream == null) {
                    remoteMediaStream = factory.createLocalMediaStream("REMOTE_STREAM")
                }

                when (track) {
                    is VideoTrack -> remoteMediaStream?.addTrack(track)
                    is AudioTrack -> remoteMediaStream?.addTrack(track)
                }

                remoteMediaStream?.let { onRemoteStreamReady(it) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "Состояние ICE: $state")
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        // Добавляем локальные треки
        localAudioTrack?.let { peerConnection?.addTrack(it) }
        localVideoTrack?.let { peerConnection?.addTrack(it) }
    }

    private fun sendSignal(targetHash: String, type: String, payload: String) {
        scope.launch {
            try {
                val envelope = JSONObject().apply {
                    put("type", type)
                    put("payload", payload)
                }
                identityRepository.sendSignaling(targetHash, envelope.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сигналлинга: ${e.message}")
            }
        }
    }

    private fun onSignalingPacket(type: String, data: String, fromHash: String) {
        if (type != "SIGNALING") return

        try {
            val json = JSONObject(data)
            val signalType = json.getString("type")
            val payload = json.getString("payload")

            when (signalType) {
                "OFFER" -> answer(fromHash, payload)
                "ANSWER" -> {
                    val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, payload)
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            isRemoteDescriptionSet = true
                            drainPendingIceCandidates()
                        }
                    }, answerSdp)
                }
                "ICE" -> {
                    val parts = payload.split("|", limit = 3)
                    if (parts.size == 3) {
                        val sdpMid = if (parts[0].isEmpty()) null else parts[0]
                        val mLineIndex = parts[1].toIntOrNull() ?: 0
                        val candidate = IceCandidate(sdpMid, mLineIndex, parts[2])
                        
                        if (isRemoteDescriptionSet) {
                            peerConnection?.addIceCandidate(candidate)
                        } else {
                            pendingIceCandidates.add(candidate)
                        }
                    }
                }
                "HANGUP" -> {
                    Log.d(TAG, "Завершение звонка от $fromHash")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки пакета: ${e.message}")
        }
    }

    private fun drainPendingIceCandidates() {
        pendingIceCandidates.forEach { candidate ->
            peerConnection?.addIceCandidate(candidate)
        }
        pendingIceCandidates.clear()
    }

    /** Очистка всех ресурсов WebRTC */
    fun release() {
        identityRepository.removeListener(signalingListener)
        
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            localVideoTrack = null
            localAudioTrack = null
            remoteMediaStream = null

            factory.dispose()
            rootEglBase.release()
            
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при закрытии ресурсов: ${e.message}")
        }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("SimpleSdpObserver", "Ошибка создания SDP: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("SimpleSdpObserver", "Ошибка установки SDP: $error")
    }
}
