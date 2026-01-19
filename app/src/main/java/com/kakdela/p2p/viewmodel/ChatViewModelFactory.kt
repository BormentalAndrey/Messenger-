package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.MessageRepository
import com.kakdela.p2p.ui.ChatViewModel

class ChatViewModelFactory(
    private val identityRepository: IdentityRepository,
    private val messageRepository: MessageRepository, // Исправлено: добавлен репозиторий
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            // Теперь передаем оба репозитория в конструктор ViewModel
            return ChatViewModel(application, identityRepository, messageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
