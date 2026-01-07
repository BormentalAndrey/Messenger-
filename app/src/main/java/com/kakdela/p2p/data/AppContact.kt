package com.kakdela.p2p.data

/**
 * Единая модель контакта для P2P мессенджера.
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val publicKey: String? = null,
    val isRegistered: Boolean = false,
    val lastKnownIp: String? = null
) {
    // Хелпер для получения безопасного ID для навигации
    fun getIdentifier(): String = publicKey ?: phoneNumber
}

