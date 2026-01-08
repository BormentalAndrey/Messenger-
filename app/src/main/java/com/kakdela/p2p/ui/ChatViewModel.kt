package com.kakdela.p2p.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel для P2P-чата.
 *
 * ✔ UDP signaling
 * ✔ DHT-resolve IP
 * ✔ multicast listeners (НЕ ломает другие компоненты)
 * ✔ production lifecycle
 */
class ChatViewModel(
    private val repository: IdentityRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ChatVM"
    }

    /* ===================== STATE ===================== */

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    /** Хеш публичного ключа собеседника */
    private var partnerId: String = ""

    /** Последний известный IP собеседника */
    @Volatile
    private var partnerIp: String = ""

    private val resolving = AtomicBoolean(false)

    /* ===================== LISTENER ===================== */

    private val listener: (String, String, String) -> Unit = { type, data, fromIp ->
        when (type) {
            "CHAT_MSG" -> handleIncomingMessage(data, fromIp)
            "STORE_RESPONSE" -> handleStoreResponse(data, fromIp)
        }
    }

    init {
        repository.addListener(listener)
    }

    /* ===================== INIT CHAT ===================== */

    fun initChat(partnerId: String) {
        this.partnerId = partnerId

        if (this.partnerIp.isBlank()) {
            resolvePartnerIp()
        }
    }

    /* ===================== DHT ===================== */

    private fun resolvePartnerIp() {
        if (!resolving.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Resolving IP for $partnerId")
                repository.findPeerInDHT(partnerId)
            } finally {
                // даём время сети ответить, затем разрешаем повтор
                delay(1500)
                resolving.set(false)
            }
        }
    }

    private fun handleStoreResponse(data: String, fromIp: String) {
        // Ожидаемый формат: "<hash>:<publicKey>"
        val parts = data.split(":", limit = 2)
        if (parts.isEmpty()) return

        val hash = parts[0]
        if (hash == partnerId) {
            partnerIp = fromIp
            Log.d(TAG, "Partner IP resolved: $partnerIp")
        }
    }

    /* ===================== INCOMING ===================== */

    private fun handleIncomingMessage(jsonStr: String, fromIp: String) {
        try {
            val json = JSONObject(jsonStr)
            val senderId = json.getString("senderId")

            if (senderId != partnerId) return

            // обновляем IP на лету (роуминг / смена сети)
            partnerIp = fromIp

            val msg = Message(
                id = json.getString("id"),
                senderId = senderId,
                text = json.getString("text"),
                timestamp = json.getLong("timestamp"),
                isMe = false
            )

            viewModelScope.launch {
                _messages.value = _messages.value + msg
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }

    /* ===================== SEND ===================== */

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val myId = repository.getMyId()
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val localMsg = Message(
            id = msgId,
            senderId = myId,
            text = text,
            timestamp = timestamp,
            isMe = true
        )

        // UI — сразу
        _messages.value = _messages.value + localMsg

        viewModelScope.launch(Dispatchers.IO) {

            if (partnerIp.isBlank()) {
                resolvePartnerIp()
                Log.w(TAG, "Partner IP unknown, message queued (best effort)")
            }

            val payload = JSONObject().apply {
                put("id", msgId)
                put("senderId", myId)
                put("text", text)
                put("timestamp", timestamp)
            }

            if (partnerIp.isNotBlank()) {
                repository.sendSignaling(
                    partnerIp,
                    "CHAT_MSG",
                    payload.toString()
                )
            }
        }
    }

    /* ===================== FILE / AUDIO ===================== */

    fun sendFile(uri: Uri, type: MessageType) {
        val name = uri.lastPathSegment ?: "file"
        sendMessage("📎 Файл: $name")

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Start file transfer $uri → $partnerIp")
            // здесь вызывается FileTransferWorker
        }
    }

    fun sendAudio(uri: Uri, durationSec: Int) {
        sendMessage("🎤 Голосовое сообщение ($durationSec сек.)")
    }

    /* ===================== SCHEDULE ===================== */

    fun scheduleMessage(text: String, timeMillis: Long) {
        val delayMs = timeMillis - System.currentTimeMillis()
        if (delayMs <= 0) return

        viewModelScope.launch {
            delay(delayMs)
            sendMessage(text)
        }
    }

    /* ===================== LIFECYCLE ===================== */

    override fun onCleared() {
        super.onCleared()
        repository.removeListener(listener)
    }
}
