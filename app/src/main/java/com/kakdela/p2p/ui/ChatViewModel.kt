package com.kakdela.p2p.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(private val repository: IdentityRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var partnerId: String = ""

    private val listener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        if (type == "MESSAGE" && fromId == partnerId) {
            handleIncomingMessage(data, fromId)
        }
    }

    init { repository.addListener(listener) }

    fun initChat(partnerId: String) {
        this.partnerId = partnerId
    }

    private fun handleIncomingMessage(text: String, fromId: String) {
        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = fromId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = false
        )
        viewModelScope.launch { _messages.value = _messages.value + msg }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerId.isBlank()) return

        val localMsg = Message(
            id = UUID.randomUUID().toString(),
            senderId = repository.getMyId(),
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = true
        )
        _messages.value = _messages.value + localMsg

        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(partnerId, text)
        }
    }

    override fun onCleared() {
        repository.removeListener(listener)
        super.onCleared()
    }
}
