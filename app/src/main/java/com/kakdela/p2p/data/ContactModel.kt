package com.kakdela.p2p.data

data class ContactModel(
    val name: String,
    val phoneNumber: String,
    val isRegistered: Boolean = false,
    val userId: String? = null
)

