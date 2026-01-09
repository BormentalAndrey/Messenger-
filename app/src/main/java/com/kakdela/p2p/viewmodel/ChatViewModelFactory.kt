package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kakdela.p2p.data.IdentityRepository

/**
 * Фабрика для создания ChatViewModel с внедрением необходимых зависимостей.
 * Мы используем Application для обеспечения работы WebRTC и доступа к файловой системе.
 */
class ChatViewModelFactory(
    private val repository: IdentityRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                // Создаем ChatViewModel, передавая репозиторий и контекст приложения
                ChatViewModel(repository, application) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
