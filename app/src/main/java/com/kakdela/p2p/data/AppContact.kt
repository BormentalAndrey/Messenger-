package com.kakdela.p2p.data

/**
 * Единая модель контакта для P2P мессенджера.
 * Оптимизирована для работы с Discovery Server (api.php).
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val isRegistered: Boolean = false,
    val userHash: String = "",        // SHA-256 ID для открытия чата
    val publicKey: String? = null,     // Публичный ключ для шифрования
    val lastKnownIp: String? = null,   // IP для прямой UDP отправки
    val isOnline: Boolean = false      // Статус присутствия в сети
) {
    /**
     * Возвращает идентификатор для навигации.
     * Приоритет отдается userHash (Security ID).
     */
    fun getIdentifier(): String = if (userHash.isNotEmpty()) userHash else phoneNumber
}
