package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.UUID

class WebRtcClient(
    private val context: Context,
    private val identityRepo: IdentityRepository,
    private val targetHash: String, 
    private val targetIp: String,   
    private val chatId: String
) {
    private val TAG = "WebRtcClient"
    private val dao = ChatDatabase.getDatabase(context).messageDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val factory: PeerConnectionFactory by lazy {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    init {
        setupPeerConnection()
        setupSignalingListener()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val iceJson = JSONObject().apply {
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                        put("sdp", it.sdp)
                    }
                    
                    val envelope = JSONObject().apply {
                        put("sub_type", "ICE_CANDIDATE")
                        put("payload", iceJson.toString())
                    }

                    // ИСПРАВЛЕНО: Вызов в корутине и 2 параметра
                    scope.launch {
                        identityRepo.sendSignaling(targetHash, envelope.toString())
                    }
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                dc?.let { setupDataChannel(it) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannel = peerConnection?.createDataChannel("p2p_chat", dcInit)
        dataChannel?.let { setupDataChannel(it) }
    }

    private fun setupDataChannel(dc: DataChannel) {
        this.dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val encryptedData = String(bytes, Charsets.UTF_8)

                scope.launch {
                    try {
                        val decryptedText = CryptoManager.decryptMessage(encryptedData)
                        val message = MessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            chatId = chatId,
                            senderId = targetHash,
                            receiverId = identityRepo.getMyId(),
                            text = decryptedText,
                            timestamp = System.currentTimeMillis(),
                            isMe = false,
                            status = "RECEIVED"
                        )
                        dao.insert(message)
                    } catch (e: Exception) { Log.e(TAG, "Decryption/DB Error", e) }
                }
            }
            override fun onStateChange() {
                Log.d(TAG, "DataChannel State: ${dataChannel?.state()}")
            }
            override fun onBufferedAmountChange(l: Long) {}
        })
    }

    private fun setupSignalingListener() {
        identityRepo.addListener { type, data, _, fromId ->
            if (fromId != targetHash || type != "WEBRTC_SIGNAL") return@addListener
            try {
                val json = JSONObject(data)
                val signalType = json.getString("sub_type")
                val signalData = json.getString("payload")

                when (signalType) {
                    "OFFER" -> handleOffer(signalData)
                    "ANSWER" -> handleAnswer(signalData)
                    "ICE_CANDIDATE" -> {
                        val iceJson = JSONObject(signalData)
                        val candidate = IceCandidate(
                            iceJson.getString("sdpMid"),
                            iceJson.getInt("sdpMLineIndex"),
                            iceJson.getString("sdp")
                        )
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Signaling Listener Error", e) }
        }
    }

    fun initiateConnection() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    
                    val envelope = JSONObject().apply {
                        put("sub_type", "OFFER")
                        put("payload", it.description)
                    }

                    // ИСПРАВЛЕНО: Вызов в корутине и 2 параметра
                    scope.launch {
                        identityRepo.sendSignaling(targetHash, envelope.toString())
                    }
                }
            }
        }, constraints)
    }

    private fun handleOffer(sdp: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        answer?.let {
                            peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                            
                            val envelope = JSONObject().apply {
                                put("sub_type", "ANSWER")
                                put("payload", it.description)
                            }

                            // ИСПРАВЛЕНО: Вызов в корутине и 2 параметра
                            scope.launch {
                                identityRepo.sendSignaling(targetHash, envelope.toString())
                            }
                        }
                    }
                }, MediaConstraints())
            }
        }, offer)
    }

    private fun handleAnswer(sdp: String) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun send(text: String): Boolean {
        if (dataChannel?.state() != DataChannel.State.OPEN) return false
        return try {
            val peerKey = CryptoManager.getPeerPublicKey(targetHash) ?: ""
            val encrypted = CryptoManager.encryptMessage(text, peerKey)
            val buffer = ByteBuffer.wrap(encrypted.toByteArray())
            dataChannel?.send(DataChannel.Buffer(buffer, false))
            true
        } catch (e: Exception) { 
            Log.e(TAG, "DataChannel send error", e)
            false 
        }
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.dispose()
        factory.dispose()
        scope.cancel()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("WebRtcClient", "SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("WebRtcClient", "SDP Set Failure: $p0") }
    }
}
