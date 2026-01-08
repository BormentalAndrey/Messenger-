package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer

/**
 * WebRtcClient для передачи текстовых сообщений и файлов через DataChannel.
 * Использует IdentityRepository для P2P-сигналинга (вместо Firebase).
 */
class WebRtcClient(
    private val context: Context,
    private val identityRepo: IdentityRepository,
    private val targetHash: String, // Кому звоним/пишем
    private val targetIp: String,   // IP получателя для сигналов
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
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // Отправляем ICE кандидаты через IdentityRepo (UDP)
                val json = JSONObject().apply {
                    put("type", "ICE_CANDIDATE")
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("sdp", candidate.sdp)
                }
                identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", json.toString())
            }

            override fun onDataChannel(dc: DataChannel) {
                Log.d(TAG, "Remote DataChannel received")
                setupDataChannel(dc)
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // Создаем свой DataChannel, если мы инициатор
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("p2p_chat", dcInit)
        dataChannel?.let { setupDataChannel(it) }
    }

    private fun setupDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)

                scope.launch {
                    val message = MessageEntity(
                        id = System.currentTimeMillis().toString(),
                        chatId = chatId,
                        senderId = targetHash,
                        text = if (buffer.binary) "" else String(bytes),
                        timestamp = System.currentTimeMillis(),
                        isMe = false, // Исправлено для Room
                        isRead = false
                    )
                    dao.insert(message)
                }
            }

            override fun onStateChange() {
                Log.d(TAG, "DataChannel State: ${dc.state()}")
            }

            override fun onBufferedAmountChange(p0: Long) {}
        })
    }

    // Обработка входящих сигналов (SDP/ICE) из IdentityRepository
    private fun setupSignalingListener() {
        identityRepo.addListener { type, data, fromIp, fromId ->
            if (fromId != targetHash || type != "WEBRTC_SIGNAL") return@addListener
            
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
        }
    }

    private fun handleOffer(sdp: String) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        peerConnection?.setLocalDescription(this, description)
                        val json = JSONObject().apply {
                            put("type", "ANSWER")
                            put("sdp", description.description)
                        }
                        identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", json.toString())
                    }
                }, MediaConstraints())
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun handleAnswer(sdp: String) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun send(text: String) {
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray()), false)
        dataChannel?.send(buffer)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        scope.cancel()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
