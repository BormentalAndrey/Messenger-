package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.webrtc.*
import java.nio.ByteBuffer

class WebRtcClient(
    private val context: Context,
    private val chatId: String,
    private val currentUserId: String,
    private val onFileReceived: (ByteArray) -> Unit
) {
    private val db = Firebase.firestore
    private val factory: PeerConnectionFactory by lazy { createFactory() }
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var pendingFile: ByteArray? = null

    init {
        setupPeerConnection()
        listenForPresenceAndSignaling()
    }

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        return PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        peerConnection = factory.createPeerConnection(PeerConnection.RTCConfiguration(iceServers), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = mapOf("sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "sdp" to candidate.sdp, "sender" to currentUserId)
                db.collection("chats").document(chatId).collection("candidates").add(data)
            }
            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
                dc.registerObserver(object : DataChannel.Observer {
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = ByteArray(buffer.data.remaining())
                        buffer.data.get(data)
                        onFileReceived(data)
                    }
                    override fun onStateChange() {
                        if (dc.state() == DataChannel.State.OPEN && pendingFile != null) {
                            dc.send(DataChannel.Buffer(ByteBuffer.wrap(pendingFile!!), false))
                            pendingFile = null
                        }
                    }
                    override fun onBufferedAmountChange(p0: Long) {}
                })
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
    }

    private fun listenForPresenceAndSignaling() {
        db.collection("chats").document(chatId).addSnapshotListener { snapshot, _ ->
            val remoteOffer = snapshot?.getString("offer")
            val remoteAnswer = snapshot?.getString("answer")
            val remoteId = if (currentUserId == "user1") "user2" else "user1" // Упрощенная логика ID
            val isRemoteOnline = snapshot?.getString("status_$remoteId") == "online"

            if (remoteOffer != null && peerConnection?.remoteDescription == null) {
                handleOffer(remoteOffer)
            } else if (remoteAnswer != null) {
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, remoteAnswer))
            }

            if (isRemoteOnline && pendingFile != null) {
                startConnection()
            }
        }
    }

    fun queueFile(bytes: ByteArray) {
        pendingFile = bytes
    }

    private fun startConnection() {
        dataChannel = peerConnection?.createDataChannel("fileTransfer", DataChannel.Init())
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                db.collection("chats").document(chatId).update("offer", desc.description)
            }
        }, MediaConstraints())
    }

    private fun handleOffer(offer: String) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, offer))
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                db.collection("chats").document(chatId).update("answer", desc.description)
            }
        }, MediaConstraints())
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

