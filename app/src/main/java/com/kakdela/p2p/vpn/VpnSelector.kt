package com.kakdela.p2p.vpn

object VpnSelector {
    fun pickBest(list: List<VpnServer>): VpnServer =
        list.minBy { it.ping }
}
