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

    init {
        setupPeerConnection()
        listenForRemoteMessages()
    }

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        return PeerConnectionFactory.builder().setOptions(PeerConnectionFactory.Options()).createPeerConnectionFactory()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = mapOf("sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "sdp" to candidate.sdp, "sender" to currentUserId)
                db.collection("chats").document(chatId).collection("candidates").add(data)
            }
            override fun onDataChannel(dc: DataChannel) {
                setupDataChannel(dc)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { Log.d("WebRTC", "State: $state") }
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

    private fun setupDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onFileReceived(data)
            }
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() { Log.d("WebRTC", "DataChannel State: ${dc.state()}") }
        })
    }

    // Инициировать соединение (отправить Offer)
    fun startConnection() {
        val dcInit = DataChannel.Init()
        setupDataChannel(peerConnection!!.createDataChannel("fileTransfer", dcInit))
        
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                db.collection("chats").document(chatId).update("offer", desc.description)
            }
        }, MediaConstraints())
    }

    private fun listenForRemoteMessages() {
        db.collection("chats").document(chatId).addSnapshotListener { snapshot, _ ->
            val offer = snapshot?.getString("offer")
            val answer = snapshot?.getString("answer")

            if (offer != null && peerConnection?.remoteDescription == null) {
                handleOffer(offer)
            } else if (answer != null && peerConnection?.remoteDescription == null) {
                peerConnection?.setRemoteDescription(SdpObserverAdapter(), SessionDescription(SessionDescription.Type.ANSWER, answer))
            }
        }
    }

    private fun handleOffer(offer: String) {
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), SessionDescription(SessionDescription.Type.OFFER, offer))
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                db.collection("chats").document(chatId).update("answer", desc.description)
            }
        }, MediaConstraints())
    }

    fun sendFile(bytes: ByteArray) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(bytes)
            dataChannel?.send(DataChannel.Buffer(buffer, false))
        }
    }
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
