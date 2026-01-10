package com.kakdela.p2p.data

/**
 * Единая продакшн-модель контакта для P2P мессенджера.
 * Используется для UI, Discovery и маршрутизации сообщений.
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,

    /** SHA-256 Security ID узла (null если не зарегистрирован) */
    val userHash: String? = null,

    /** Публичный ключ для E2E шифрования */
    val publicKey: String? = null,

    /** Последний известный IP (для UDP / P2P) */
    val lastKnownIp: String? = null,

    /** Онлайн-статус из Discovery / Swarm */
    val isOnline: Boolean = false
) {

    /**
     * Контакт считается зарегистрированным,
     * если у него есть валидный userHash.
     */
    val isRegistered: Boolean
        get() = !userHash.isNullOrBlank()

    /**
     * Гарантированно валидный идентификатор для:
     * - навигации
     * - ключей RecyclerView
     * - открытия чата
     */
    fun getIdentifier(): String =
        userHash ?: phoneNumber
}
