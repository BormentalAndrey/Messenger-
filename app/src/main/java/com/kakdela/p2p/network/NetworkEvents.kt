package com.kakdela.p2p.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NetworkEvents {
    // extraBufferCapacity позволяет не блокировать сетевой поток при отправке события
    private val _onAuthRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onAuthRequired = _onAuthRequired.asSharedFlow()

    /**
     * Вызывается из OkHttp Interceptor, когда сервер вернул HTML вместо JSON
     */
    fun triggerAuth() {
        _onAuthRequired.tryEmit(Unit)
    }
}
