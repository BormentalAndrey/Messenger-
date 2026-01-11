package com.kakdela.p2p.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NetworkEvents {
    private val _onAuthRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onAuthRequired = _onAuthRequired.asSharedFlow()

    fun triggerAuth() {
        _onAuthRequired.tryEmit(Unit)
    }
}
