package com.kakdela.p2p.auth

/**
 * Синглтон для временного хранения кода из SMS.
 * Используется AuthViewModel для автоматического заполнения полей ввода.
 */
object SmsCodeStore {
    var lastReceivedCode: String? = null

    fun clear() {
        lastReceivedCode = null
    }
}
