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
        // Проверяем тип WEBRTC_SIGNAL, так как sendSignaling шлет именно его
        if (type == "WEBRTC_SIGNAL") {
            try {
                val json = JSONObject(data)
                val signalType = json.optString("type")
                val signalData = json.optString("data")
                
                when (signalType) {
                    "FILE_CHUNK_REQ" -> handleChunkRequest(signalData, fromIp)
                    "FILE_CHUNK_DATA" -> handleChunkData(signalData)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки парсинга не файловых сигналов
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

    private fun handleChunkRequest(jsonData: String, targetIp: String) {
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

                    val response = JSONObject().apply {
                        put("file_id", fileId)
                        put("chunk_index", chunkIndex)
                        put("data", Base64.encodeToString(if (read == buffer.size) buffer else buffer.copyOf(read), Base64.NO_WRAP))
                    }
                    // FIX: передаем 3 параметра
                    identityRepo.sendSignaling(targetIp, "FILE_CHUNK_DATA", response.toString())
                }
            } catch (e: Exception) { Log.e("P2P_FILE", "Error", e) }
        }
    }

    suspend fun downloadFileP2P(targetIp: String, fileId: String, fileName: String, totalChunks: Int): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val session = DownloadSession(fileId, outputFile, totalChunks)
        activeDownloads[fileId] = session

        for (i in 0 until totalChunks) {
            val req = JSONObject().apply { 
                put("file_id", fileId)
                put("chunk_index", i) 
            }
            // FIX: передаем 3 параметра
            identityRepo.sendSignaling(targetIp, "FILE_CHUNK_REQ", req.toString())
            delay(20) // Небольшая задержка, чтобы не забить UDP буфер
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
            } catch (e: Exception) { }
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
        fun await() = latch.await()
    }
}
