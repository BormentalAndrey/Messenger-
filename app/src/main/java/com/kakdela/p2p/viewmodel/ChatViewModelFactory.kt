package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.ChatViewModel // ИМПОРТ ИЗ UI ПАКЕТА ОБЯЗАТЕЛЕН

class ChatViewModelFactory(
    private val repository: IdentityRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            // AndroidViewModel обычно принимает Application первым параметром
            return ChatViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
