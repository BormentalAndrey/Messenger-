package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class FileTransferWorker(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedingFiles = ConcurrentHashMap<String, File>()
    private val activeDownloads = ConcurrentHashMap<String, DownloadSession>()

    private val listener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        // Проверяем тип WEBRTC_SIGNAL, так как IdentityRepository.sendSignaling шлет именно его
        if (type == "WEBRTC_SIGNAL") {
            try {
                val json = JSONObject(data)
                val signalType = json.optString("sub_type") // Используем sub_type для различения сигналов внутри WEBRTC_SIGNAL
                val signalData = json.optString("payload")
                
                when (signalType) {
                    "FILE_CHUNK_REQ" -> handleChunkRequest(signalData, fromId) // Используем ID отправителя для обратного пути
                    "FILE_CHUNK_DATA" -> handleChunkData(signalData)
                }
            } catch (e: Exception) {
                Log.e("P2P_FILE", "Signal parsing error", e)
            }
        }
    }

    init { identityRepo.addListener(listener) }

    fun release() {
        identityRepo.removeListener(listener)
        scope.cancel()
    }

    fun addFileToSeeding(file: File): String {
        val fileId = UUID.randomUUID().toString()
        seedingFiles[fileId] = file
        return fileId
    }

    private fun handleChunkRequest(jsonData: String, targetHash: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val chunkIndex = json.getInt("chunk_index")
                val file = seedingFiles[fileId] ?: return@launch

                RandomAccessFile(file, "r").use { raf ->
                    val offset = chunkIndex.toLong() * (16 * 1024)
                    if (offset >= file.length()) return@use
                    raf.seek(offset)
                    val buffer = ByteArray(16 * 1024)
                    val read = raf.read(buffer)
                    if (read <= 0) return@use

                    val chunkDataJson = JSONObject().apply {
                        put("file_id", fileId)
                        put("chunk_index", chunkIndex)
                        put("data", Base64.encodeToString(if (read == buffer.size) buffer else buffer.copyOf(read), Base64.NO_WRAP))
                    }

                    // Упаковываем в формат, который ожидает наш слушатель
                    val envelope = JSONObject().apply {
                        put("sub_type", "FILE_CHUNK_DATA")
                        put("payload", chunkDataJson.toString())
                    }

                    // ИСПРАВЛЕНО: Теперь передаем ровно 2 параметра
                    identityRepo.sendSignaling(targetHash, envelope.toString())
                }
            } catch (e: Exception) { Log.e("P2P_FILE", "Error handling chunk request", e) }
        }
    }

    suspend fun downloadFileP2P(targetHash: String, fileId: String, fileName: String, totalChunks: Int): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val session = DownloadSession(fileId, outputFile, totalChunks)
        activeDownloads[fileId] = session

        for (i in 0 until totalChunks) {
            val reqData = JSONObject().apply { 
                put("file_id", fileId)
                put("chunk_index", i) 
            }

            val envelope = JSONObject().apply {
                put("sub_type", "FILE_CHUNK_REQ")
                put("payload", reqData.toString())
            }

            // ИСПРАВЛЕНО: Теперь передаем ровно 2 параметра
            identityRepo.sendSignaling(targetHash, envelope.toString())
            delay(30) // Небольшая задержка для стабильности UDP
        }
        
        session.await()
        activeDownloads.remove(fileId)
        outputFile
    }

    private fun handleChunkData(jsonData: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val session = activeDownloads[fileId] ?: return@launch
                val bytes = Base64.decode(json.getString("data"), Base64.NO_WRAP)

                RandomAccessFile(session.file, "rw").use { raf ->
                    raf.seek(json.getInt("chunk_index").toLong() * (16 * 1024))
                    raf.write(bytes)
                }
                session.markChunkReceived(json.getInt("chunk_index"))
            } catch (e: Exception) { 
                Log.e("P2P_FILE", "Error handling chunk data", e)
            }
        }
    }

    private class DownloadSession(val fileId: String, val file: File, val totalChunks: Int) {
        private val received = BooleanArray(totalChunks)
        private val latch = CountDownLatch(totalChunks)
        
        fun markChunkReceived(index: Int) {
            synchronized(received) { 
                if (index < totalChunks && !received[index]) { 
                    received[index] = true
                    latch.countDown() 
                } 
            }
        }
        
        fun await() {
            // Ожидание с таймаутом, чтобы воркер не завис навсегда при потере пакетов
            latch.await() 
        }
    }
}
