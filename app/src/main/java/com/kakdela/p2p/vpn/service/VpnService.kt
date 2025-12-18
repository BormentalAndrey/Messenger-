package com.kakdela.p2p.vpn.service

import android.content.Intent
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.model.VpnServer

// ... (остальные импорты)

// Внутри класса VpnService, где происходит запуск:
private fun startVpn(server: VpnServer) {
    val vpnBackend = VpnBackend(this)
    val myPrivateKey = "ВАШ_ПРИВАТНЫЙ_КЛЮЧ_ИЗ_KEYSTORE" // Должен быть Base64

    // 1. Создаем объект Config
    val config = vpnBackend.buildConfig(server, myPrivateKey)

    // 2. Запускаем (теперь аргумент только один)
    vpnBackend.up(config)
}

private fun stopVpn() {
    val vpnBackend = VpnBackend(this)
    // 3. Останавливаем (без аргументов)
    vpnBackend.down()
}

