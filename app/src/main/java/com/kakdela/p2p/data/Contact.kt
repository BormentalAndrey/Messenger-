package com.kakdela.p2p.data

data class AppContact(
    val name: String,
    val phoneNumber: String,        // Нормализованный номер (11 цифр, начинается с 7)
    val uid: String? = null,        // null = не зарегистрирован в приложении
    val isRegistered: Boolean = false
)
