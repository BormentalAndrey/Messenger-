package com.kakdela.p2p.data

import android.content.Context
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import org.webrtc.*
import java.nio.ByteBuffer

class WebRtcClient(
    private val context: Context,
    private val chatId: String,
    private val currentUserId: String
) {
    private val db = Firebase.firestore
    private val dao = ChatDatabase.getDatabase(context).messageDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val factory: PeerConnectionFactory by lazy { createFactory() }
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    init {
        setupPeerConnection()
        listenForSignaling()
    }

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        return PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "sdp" to candidate.sdp,
                    "sender" to currentUserId
                )
                db.collection("chats").document(chatId).collection("candidates").add(data)
            }
            override fun onDataChannel(dc: DataChannel) { setupDataChannel(dc) }
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
                    val entity = if (buffer.binary) {
                        MessageEntity(chatId = chatId, text = "", fileBytes = bytes, senderId = "remote", timestamp = System.currentTimeMillis())
                    } else {
                        MessageEntity(chatId = chatId, text = String(bytes, Charsets.UTF_8), senderId = "remote", timestamp = System.currentTimeMillis())
                    }
                    dao.insert(entity)
                }
            }
            override fun onStateChange() {}
            override fun onBufferedAmountChange(p0: Long) {}
        })
    }

    fun sendP2P(text: String, bytes: ByteArray? = null) {
        scope.launch {
            dao.insert(MessageEntity(chatId = chatId, text = text, fileBytes = bytes, senderId = currentUserId, timestamp = System.currentTimeMillis()))
            
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                val buffer = if (bytes != null) DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
                else DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false)
                dataChannel?.send(buffer)
            } else {
                startConnection()
            }
        }
    }

    private fun startConnection() {
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("p2pChannel", dcInit)
        dataChannel?.let { setupDataChannel(it) }

        peerConnection?.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), desc)
                db.collection("chats").document(chatId).set(mapOf("offer" to desc.description), com.google.firebase.firestore.SetOptions.merge())
            }
        }, MediaConstraints())
    }

    private fun listenForSignaling() {
        db.collection("chats").document(chatId).addSnapshotListener { snapshot, _ ->
            val offer = snapshot?.getString("offer")
            val answer = snapshot?.getString("answer")
            if (offer != null && peerConnection?.remoteDescription == null) {
                handleOffer(offer)
            } else if (answer != null) {
                peerConnection?.setRemoteDescription(SdpAdapter(), SessionDescription(SessionDescription.Type.ANSWER, answer))
            }
        }
        db.collection("chats").document(chatId).collection("candidates").addSnapshotListener { snapshot, _ ->
            snapshot?.documentChanges?.forEach { change ->
                val d = change.document.data
                if (d["sender"] != currentUserId) {
                    val candidate = IceCandidate(d["sdpMid"] as String, (d["sdpMLineIndex"] as Long).toInt(), d["sdp"] as String)
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun handleOffer(offer: String) {
        peerConnection?.setRemoteDescription(SdpAdapter(), SessionDescription(SessionDescription.Type.OFFER, offer))
        peerConnection?.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), desc)
                db.collection("chats").document(chatId).update("answer", desc.description)
            }
        }, MediaConstraints())
    }
}

open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

