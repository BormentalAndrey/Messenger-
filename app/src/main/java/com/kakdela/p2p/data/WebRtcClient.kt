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

/**
 * WebRtcClient для P2P передачи данных через WebRTC DataChannel.
 * Использует IdentityRepository (UDP) как сигнальный сервер.
 */
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
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("type", "ICE_CANDIDATE")
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                        put("sdp", it.sdp)
                    }
                    identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", json.toString())
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                dc?.let { 
                    Log.d(TAG, "DataChannel received from remote")
                    setupDataChannel(it) 
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE State changed: $state")
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

        // Создаем локальный DataChannel (мы выступаем инициатором)
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("p2p_chat_channel", dcInit)
        dataChannel?.let { setupDataChannel(it) }
    }

    private fun setupDataChannel(dc: DataChannel) {
        this.dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val rawData = String(bytes, Charsets.UTF_8)

                scope.launch {
                    // Дешифруем, если сообщение было зашифровано через CryptoManager
                    val decryptedText = CryptoManager.decryptMessage(rawData)
                    
                    val message = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        senderId = targetHash,
                        receiverId = identityRepo.getMyId(),
                        text = decryptedText,
                        timestamp = System.currentTimeMillis(),
                        isMe = false,
                        isRead = false,
                        encrypted = true
                    )
                    dao.insert(message)
                }
            }

            override fun onStateChange() {
                Log.d(TAG, "DataChannel State: ${dc.state()}")
            }

            override fun onBufferedAmountChange(l: Long) {}
        })
    }

    private fun setupSignalingListener() {
        identityRepo.addListener { type, data, _, fromId ->
            if (fromId != targetHash || type != "WEBRTC_SIGNAL") return@addListener
            
            try {
                val json = JSONObject(data)
                when (json.getString("type")) {
                    "OFFER" -> handleOffer(json.getString("sdp"))
                    "ANSWER" -> handleAnswer(json.getString("sdp"))
                    "ICE_CANDIDATE" -> {
                        val candidate = IceCandidate(
                            json.getString("sdpMid"),
                            json.getInt("sdpMLineIndex"),
                            json.getString("sdp")
                        )
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signaling parsing error", e)
            }
        }
    }

    /**
     * Инициация соединения (создание OFFER)
     */
    fun initiateConnection() {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            val json = JSONObject().apply {
                                put("type", "OFFER")
                                put("sdp", sdp.description)
                            }
                            identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", json.toString())
                        }
                    }, sdp)
                }
            }
        }, MediaConstraints())
    }

    private fun handleOffer(sdp: String) {
        val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        description?.let { answerSdp ->
                            peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    val json = JSONObject().apply {
                                        put("type", "ANSWER")
                                        put("sdp", answerSdp.description)
                                    }
                                    identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", json.toString())
                                }
                            }, answerSdp)
                        }
                    }
                }, MediaConstraints())
            }
        }, remoteDescription)
    }

    private fun handleAnswer(sdp: String) {
        val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), remoteDescription)
    }

    fun send(text: String) {
        if (dataChannel?.state() != DataChannel.State.OPEN) {
            Log.e(TAG, "Cannot send: DataChannel is ${dataChannel?.state()}")
            return
        }
        
        // Шифруем перед отправкой
        val encrypted = CryptoManager.encryptMessage(text, CryptoManager.getPeerPublicKey(targetHash) ?: "")
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(encrypted.toByteArray(Charsets.UTF_8)), false)
        dataChannel?.send(buffer)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        scope.cancel()
    }

    /**
     * Исправленный SdpObserver: параметры помечены как nullable для совместимости с библиотекой
     */
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e("SDP", "Create Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e("SDP", "Set Failure: $error") }
    }
}
