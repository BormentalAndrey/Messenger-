package com.kakdela.p2p.ui.call

import android.Manifest
import android.content.Context
import android.media.AudioManager
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

    // State
    private var targetHash: String = "" 
    private var isIncoming by mutableStateOf(false)
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    @Volatile private var isRemoteSdpSet = false

    private val signalingListener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        if (fromId == targetHash && type == "WEBRTC_SIGNAL") {
            try {
                val json = JSONObject(data)
                val signalSubtype = json.optString("sub_type")
                val payload = json.optString("payload")

                when (signalSubtype) {
                    "OFFER" -> handleIncomingOffer(payload)
                    "ANSWER" -> handleAnswer(payload)
                    "ICE" -> handleRemoteIce(payload)
                    "HANGUP" -> finish()
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Signaling error: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Поверх блокировки
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        val app = application as MyApplication
        identityRepo = app.identityRepository
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        targetHash = intent.getStringExtra("chatId") ?: intent.getStringExtra("targetHash") ?: ""
        isIncoming = intent.getBooleanExtra("isIncoming", false)
        val remoteSdp = intent.getStringExtra("remoteSdp")

        identityRepo.addListener(signalingListener)
        initWebRTC()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                startCallProcess(isIncoming, remoteSdp)
            } else {
                Toast.makeText(this, "Необходимы разрешения для звонка", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))

        setContent {
            CallUI(
                localTrack = localVideoTrack,
                remoteTrack = remoteVideoTrack,
                eglBaseContext = eglBase.eglBaseContext,
                chatPartnerName = "Абонент ${targetHash.take(6)}",
                isIncoming = isIncoming,
                onAccept = { 
                    isIncoming = false 
                    // Обработка уже запущена через startCallProcess
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
                onToggleSpeaker = { isSpeaker -> toggleSpeaker(isSpeaker) },
                onToggleVideo = { isVideo -> localVideoTrack?.setEnabled(isVideo) },
                onStartRecording = { 
                    Toast.makeText(this, "Запись начата локально", Toast.LENGTH_SHORT).show()
                },
                onAddUser = {
                    Toast.makeText(this, "Групповые звонки в разработке", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * Исправленная функция отправки сигнала: теперь получает IP из репозитория
     */
    private fun sendCallSignal(subtype: String, payload: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Ищем IP по хешу в Wi-Fi peers или swarm
            val ip = identityRepo.wifiPeers[targetHash] 
                ?: identityRepo.swarmPeers[targetHash]
                ?: identityRepo.fetchAllNodesFromServer().find { it.hash == targetHash }?.ip
            
            if (ip == null || ip == "0.0.0.0") {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Cannot send signal: IP for $targetHash not found")
                }
                return@launch
            }

            val envelope = JSONObject().apply {
                put("sub_type", subtype)
                put("payload", payload)
            }
            
            // Вызов метода с 3 параметрами, как в IdentityRepository
            identityRepo.sendSignaling(ip, "WEBRTC_SIGNAL", envelope.toString())
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

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions())
        
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                sendCallSignal("ICE", payload)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) { remoteVideoTrack = track }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                    runOnUiThread { finish() }
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
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
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        val videoSource = factory.createVideoSource(false)
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val enumerator = Camera2Enumerator(this)
        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames[0]
        
        videoCapturer = enumerator.createCapturer(deviceName, null)
        videoCapturer?.initialize(surfaceHelper, this, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
        peerConnection?.addTrack(localVideoTrack)
        
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    private fun makeOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
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
                peerConnection?.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peerConnection?.setLocalDescription(SdpAdapter(), desc)
                        sendCallSignal("ANSWER", desc.description)
                    }
                }, MediaConstraints())
            }
        }, offer)
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
            val candidate = IceCandidate(parts[0], parts[1].toInt(), parts[2])
            if (isRemoteSdpSet) peerConnection?.addIceCandidate(candidate)
            else pendingIce.add(candidate)
        } catch (e: Exception) { Log.e(TAG, "ICE Candidate error") }
    }

    private fun drainIce() {
        pendingIce.forEach { peerConnection?.addIceCandidate(it) }
        pendingIce.clear()
    }

    private fun toggleSpeaker(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    override fun onDestroy() {
        identityRepo.removeListener(signalingListener)
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.dispose()
        factory.dispose()
        eglBase.release()
        audioManager.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("CallActivity", "SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("CallActivity", "SDP Set Failure: $p0") }
    }
}
