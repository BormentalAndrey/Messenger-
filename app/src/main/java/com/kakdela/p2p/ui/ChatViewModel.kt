package com.kakdela.p2p.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val repository: IdentityRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var partnerId: String = ""

    // Слушатель входящих P2P сигналов
    private val listener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        if (type == "MESSAGE" && fromId == partnerId) {
            handleIncomingMessage(data, fromId)
        }
    }

    init {
        repository.addListener(listener)
    }

    fun initChat(partnerId: String) {
        this.partnerId = partnerId
        // Здесь можно загрузить историю из БД, если требуется
    }

    private fun handleIncomingMessage(text: String, fromId: String) {
        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = fromId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = false
        )
        // Используем update для потокобезопасности
        _messages.update { currentList -> currentList + msg }
    }

    /**
     * Исправлено: метод отправки теперь корректно взаимодействует с IdentityRepository
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerId.isBlank()) return

        val myId = repository.getMyId()
        val localMsg = Message(
            id = UUID.randomUUID().toString(),
            senderId = myId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = true
        )

        // Мгновенное обновление UI
        _messages.update { it + localMsg }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Вызываем метод сигналинга в репозитории для передачи сообщения
                repository.sendSignaling(
                    targetIp = "", // IP должен быть разрешен через репозиторий/DHT
                    type = "MESSAGE",
                    data = text
                )
            } catch (e: Exception) {
                // Обработка ошибки отправки (например, пометка сообщения как не доставленного)
            }
        }
    }

    override fun onCleared() {
        repository.removeListener(listener)
        super.onCleared()
    }
}
