package com.kakdela.p2p.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.*
import org.webrtc.*
import java.nio.ByteBuffer

class WebRtcClient(
    context: Context,
    private val chatId: String,
    private val currentUserId: String
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val dao = ChatDatabase.getDatabase(context).messageDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    init {
        setupPeerConnection()
        listenForSignaling()
    }

    private fun setupPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                firestore.collection("chats").document(chatId)
                    .collection("candidates")
                    .add(
                        mapOf(
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "sdp" to candidate.sdp,
                            "sender" to currentUserId
                        )
                    )
            }

            override fun onDataChannel(dc: DataChannel) = setupDataChannel(dc)

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
    }

    private fun setupDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)

                scope.launch {
                    dao.insert(
                        MessageEntity(
                            id = System.currentTimeMillis().toString(),
                            chatId = chatId,
                            senderId = "remote",
                            text = if (buffer.binary) "" else String(bytes),
                            timestamp = System.currentTimeMillis(),
                            fileBytes = if (buffer.binary) bytes else null
                        )
                    )
                }
            }

            override fun onStateChange() {}
            override fun onBufferedAmountChange(p0: Long) {}
        })
    }

    fun send(text: String) {
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray()), false)
        dataChannel?.send(buffer)
    }

    private fun listenForSignaling() {}
}
