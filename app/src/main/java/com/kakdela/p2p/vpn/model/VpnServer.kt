package com.kakdela.p2p.vpn.model

data class VpnServer(
    val name: String,
    val country: String,
    val host: String,
    val port: Int,
    val publicKey: String
)
