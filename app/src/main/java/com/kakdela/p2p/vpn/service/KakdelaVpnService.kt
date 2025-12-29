class KakdelaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ACTION_CONNECT"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN
            )
        } else {
            startForeground(1, notification)
        }

        // TODO: тут твой WireGuard / tun setup
    }

    private fun createNotification(): Notification {
        val channelId = "vpn"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kakdela VPN")
            .setContentText("VPN активен")
            .setOngoing(true)
            .build()
    }
}
