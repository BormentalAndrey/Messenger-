package com.kakdela.p2p.data

/**
 * Единая модель контакта приложения.
 *
 * @param name отображаемое имя контакта
 * @param phoneNumber номер телефона (E.164 или локальный)
 * @param userHash хэш пользователя в P2P-сети (null, если не зарегистрирован)
 * @param publicKey публичный ключ для шифрования
 * @param lastKnownIp последний известный IP (LAN / WAN)
 * @param isOnline признак онлайн-статуса
 * @param isRegistered зарегистрирован ли пользователь в P2P-сети
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

    /**
     * Уникальный идентификатор контакта для открытия чата:
     * - userHash, если контакт зарегистрирован (приоритет P2P)
     * - иначе phoneNumber (фоллбэк на SMS/Invite)
     */
    fun getIdentifier(): String = userHash ?: phoneNumber
}
