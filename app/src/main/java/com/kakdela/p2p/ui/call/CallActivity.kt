package com.kakdela.p2p.ui.call

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.kakdela.p2p.MyApplication
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList

class CallActivity : ComponentActivity() {

    private val TAG = "CallActivity"
    private lateinit var identityRepo: IdentityRepository
    private lateinit var factory: PeerConnectionFactory
    private lateinit var audioManager: AudioManager

    private var peerConnection: PeerConnection? = null
    private val eglBase = EglBase.create()

    // Tracks & Capturers
    private var localVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // State
    private var targetHash: String = ""
    private var isIncoming by mutableStateOf(false)
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    @Volatile private var isRemoteSdpSet = false

    private val signalingListener: (String, String, String, String) -> Unit = { type, data, _, fromId ->
        if (fromId == targetHash && type == "WEBRTC_SIGNAL") {
            try {
                val json = JSONObject(data)
                val signalSubtype = json.optString("sub_type")
                val payload = json.optString("payload")

                when (signalSubtype) {
                    "OFFER" -> handleIncomingOffer(payload)
                    "ANSWER" -> handleAnswer(payload)
                    "ICE" -> handleRemoteIce(payload)
                    "HANGUP" -> runOnUiThread { finish() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signaling error: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Поддерживаем экран включенным и отображаем поверх блокировки
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val app = application as MyApplication
        identityRepo = app.identityRepository
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        targetHash = intent.getStringExtra("chatId")
            ?: intent.getStringExtra("targetHash") ?: ""

        isIncoming = intent.getBooleanExtra("isIncoming", false)
        val remoteSdp = intent.getStringExtra("remoteSdp")

        identityRepo.addListener(signalingListener)
        
        // Инициализация WebRTC и разрешений
        initWebRTC()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                startCallProcess(isIncoming, remoteSdp)
            } else {
                Toast.makeText(this, "Разрешения камеры и микрофона обязательны", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )

        setContent {
            CallUI(
                localTrack = localVideoTrack,
                remoteTrack = remoteVideoTrack,
                eglBaseContext = eglBase.eglBaseContext,
                chatPartnerName = "Абонент ${targetHash.take(6)}",
                isIncoming = isIncoming,
                onAccept = {
                    isIncoming = false
                    // Процесс уже может быть в ожидании, вызываем handleIncomingOffer если sdp пришел ранее
                },
                onReject = {
                    sendCallSignal("HANGUP", "rejected")
                    finish()
                },
                onHangup = {
                    sendCallSignal("HANGUP", "end")
                    finish()
                },
                onToggleMute = { isMuted -> localAudioTrack?.setEnabled(!isMuted) },
                onToggleSpeaker = { enabled -> toggleSpeaker(enabled) },
                onToggleVideo = { enabled -> localVideoTrack?.setEnabled(enabled) },
                onStartRecording = {
                    Toast.makeText(this, "Запись не реализована в данной версии", Toast.LENGTH_SHORT).show()
                },
                onAddUser = {
                    Toast.makeText(this, "Групповые звонки в разработке", Toast.LENGTH_SHORT).show()
                }
            )
        }
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
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // ИСПРАВЛЕНО: Чистый строковый формат для передачи
                val payload = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                sendCallSignal("ICE", payload)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED
                ) {
                    runOnUiThread { finish() }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    private fun setupLocalStream() {
        // Аудио
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        // Видео
        val videoSource = factory.createVideoSource(false)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val enumerator = Camera2Enumerator(this)

        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()

        if (deviceName != null) {
            videoCapturer = enumerator.createCapturer(deviceName, null)
            videoCapturer?.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
            peerConnection?.addTrack(localVideoTrack)
        }

        // Настройка аудио фокуса и режима
        requestAudioConfig()
    }

    private fun requestAudioConfig() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true // По умолчанию для видеозвонков

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun startCallProcess(incoming: Boolean, sdp: String?) {
        setupLocalStream()
        if (incoming && sdp != null) {
            handleIncomingOffer(sdp)
        } else if (!incoming) {
            makeOffer()
        }
    }

    private fun makeOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), desc)
                sendCallSignal("OFFER", desc.description)
            }
        }, constraints)
    }

    private fun handleIncomingOffer(sdpStr: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        peerConnection?.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                isRemoteSdpSet = true
                drainIce()
                createAnswer()
            }
        }, offer)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), desc)
                sendCallSignal("ANSWER", desc.description)
            }
        }, constraints)
    }

    private fun handleAnswer(sdpStr: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
        peerConnection?.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                isRemoteSdpSet = true
                drainIce()
            }
        }, answer)
    }

    private fun handleRemoteIce(data: String) {
        try {
            val parts = data.split("|")
            if (parts.size < 3) return
            val candidate = IceCandidate(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
            if (isRemoteSdpSet) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingIce.add(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ICE Candidate error: ${e.message}", e)
        }
    }

    private fun drainIce() {
        for (candidate in pendingIce) {
            peerConnection?.addIceCandidate(candidate)
        }
        pendingIce.clear()
    }

    private fun sendCallSignal(subtype: String, payload: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ip = identityRepo.wifiPeers[targetHash]
                ?: identityRepo.swarmPeers[targetHash]
                ?: identityRepo.fetchAllNodesFromServer().find { it.hash == targetHash }?.ip

            if (ip == null || ip == "0.0.0.0") {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "IP not found for $targetHash")
                }
                return@launch
            }

            val envelope = JSONObject().apply {
                put("sub_type", subtype)
                put("payload", payload)
            }
            identityRepo.sendSignaling(ip, "WEBRTC_SIGNAL", envelope.toString())
        }
    }

    private fun toggleSpeaker(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    override fun onDestroy() {
        identityRepo.removeListener(signalingListener)
        
        // ИСПРАВЛЕНО: Безопасный порядок очистки ресурсов
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            
            factory.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
        
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.abandonAudioFocus(null)
        super.onDestroy()
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e("SdpAdapter", "Create Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e("SdpAdapter", "Set Failure: $error") }
    }
}
