package com.kakdela.p2p.data

/**
 * Единая модель контакта для P2P мессенджера.
 * @param name Имя из телефонной книги
 * @param phoneNumber Нормализованный номер (например, 79991234567)
 * @param publicKey Публичный ключ, найденный в DHT (используется как ID)
 * @param isRegistered Указывает, найден ли пользователь в P2P сети
 * @param lastKnownIp Актуальный IP адрес для отправки UDP пакетов
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val publicKey: String? = null,
    val isRegistered: Boolean = false,
    val lastKnownIp: String? = null
)
