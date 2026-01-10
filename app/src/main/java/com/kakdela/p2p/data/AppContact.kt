package com.kakdela.p2p.data

/**
 * Единая модель контакта.
 * isRegistered добавлен в конструктор для совместимости с ContactP2PManager.
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val userHash: String? = null,
    val publicKey: String? = null,
    val lastKnownIp: String? = null,
    val isOnline: Boolean = false,
    val isRegistered: Boolean = false 
) {
    fun getIdentifier(): String = userHash ?: phoneNumber
}
