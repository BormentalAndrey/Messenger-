package com.kakdela.p2p.data

data class AppContact(
    val name: String,
    val phoneNumber: String,
    val uid: String? = null,
    val publicKey: String? = null, // Ключ для шифрования сообщений этому юзеру
    val isRegistered: Boolean = false
)

