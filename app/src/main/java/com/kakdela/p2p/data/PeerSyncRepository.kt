package com.kakdela.p2p.data

import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * PeerSyncRepository — движок "сплетен" (Gossip Protocol).
 * Позволяет узлам обмениваться списками контактов без сервера.
 */
class PeerSyncRepository(
    private val identityRepository: IdentityRepository,
    database: ChatDatabase
) {

    private val TAG = "PeerSyncRepository"
    private val nodeDao = database.nodeDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Защита от спама: хеш узла -> время последней синхронизации
    private val lastSyncFromPeer = ConcurrentHashMap<String, Long>()

    private companion object {
        const val SYNC_INTERVAL = 60_000L
        const val MAX_NODES_PER_PACKET = 8
        const val PEER_SYNC_TYPE = "PEER_SYNC"
        const val MIN_SYNC_INTERVAL_FROM_PEER = 30_000L
    }

    fun start() {
        scope.launch {
            delay(5000) // Даем время на инициализацию UDP
            startGossipLoop()
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun startGossipLoop() {
        while (scope.isActive) {
            try {
                performGossipSync()
            } catch (e: Exception) {
                Log.w(TAG, "Gossip iteration skipped: ${e.message}")
            }
            delay(SYNC_INTERVAL)
        }
    }

    private suspend fun performGossipSync() {
        // 1. Собираем кандидатов для отправки (активные + недавние из истории)
        val candidates = (identityRepository.swarmPeers.keys + identityRepository.wifiPeers.keys).toMutableSet()
        
        if (candidates.isEmpty()) {
            // Если нет активных соединений, берем 5 недавних из БД
            candidates.addAll(nodeDao.getRecentNodes(5).map { it.userHash })
        }
        
        // Исключаем себя
        candidates.remove(identityRepository.getMyId())
        if (candidates.isEmpty()) return

        // 2. Выбираем 2 случайные цели
        val targets = candidates.shuffled().take(2)

        // 3. Выбираем, о ком рассказать (случайная выборка из всей БД)
        val nodesToSend = nodeDao.getAllNodes().shuffled().take(MAX_NODES_PER_PACKET)
        if (nodesToSend.isEmpty()) return

        val payload = buildSyncPayload(nodesToSend)

        // 4. Отправляем
        targets.forEach { targetHash ->
            // Пытаемся найти IP (в памяти или в БД)
            val ip = identityRepository.swarmPeers[targetHash]
                ?: identityRepository.wifiPeers[targetHash]
                ?: nodeDao.getNodeByHash(targetHash)?.ip

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                identityRepository.sendUdp(ip!!, PEER_SYNC_TYPE, payload)
            }
        }
    }

    suspend fun handleIncoming(data: String, fromHash: String) {
        val now = System.currentTimeMillis()
        val last = lastSyncFromPeer[fromHash] ?: 0
        if (now - last < MIN_SYNC_INTERVAL_FROM_PEER) return
        lastSyncFromPeer[fromHash] = now

        try {
            val json = JSONObject(data)
            val nodesArray = json.optJSONArray("nodes") ?: return
            mergeIncomingNodes(nodesArray)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse gossip packet", e)
        }
    }

    private suspend fun mergeIncomingNodes(nodes: JSONArray) {
        withContext(Dispatchers.IO) {
            for (i in 0 until nodes.length()) {
                val obj = nodes.getJSONObject(i)
                val hash = obj.getString("hash")
                
                if (hash == identityRepository.getMyId()) continue

                val ip = obj.optString("ip", "0.0.0.0")
                val port = obj.optInt("port", 8888) 
                val publicKey = obj.optString("publicKey", "")
                val lastSeen = obj.optLong("lastSeen", 0L) 

                if (hash.isBlank() || publicKey.isBlank()) continue

                val local = nodeDao.getNodeByHash(hash)

                if (local == null) {
                    // Новый узел -> сохраняем и пингуем
                    nodeDao.upsert(
                        NodeEntity(
                            userHash = hash,
                            phone_hash = "",
                            ip = ip,
                            port = port,
                            publicKey = publicKey,
                            phone = "",
                            lastSeen = lastSeen
                        )
                    )
                    if (ip != "0.0.0.0") identityRepository.sendUdp(ip, "PING", "gossip_discovery")
                } else {
                    // Обновляем, только если данные свежее
                    if (lastSeen > local.lastSeen) {
                        nodeDao.updateNetworkInfo(hash, ip, port, local.publicKey, lastSeen)
                    }
                }
            }
        }
    }

    private fun buildSyncPayload(nodes: List<NodeEntity>): String {
        val arr = JSONArray()
        nodes.forEach {
            val obj = JSONObject().apply {
                put("hash", it.userHash)
                put("ip", it.ip)
                put("port", it.port)
                put("publicKey", it.publicKey)
                put("lastSeen", it.lastSeen)
            }
            arr.put(obj)
        }
        return JSONObject().put("nodes", arr).toString()
    }
}
