package com.kakdela.p2p.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.json.JSONObject

class FileTransferWorker(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    private val CHUNK_SIZE = 16384 // 16 KB
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transferSocket = DatagramSocket()

    // Хранилище файлов, которые мы сейчас "раздаем"
    private val seedingFiles = mutableMapOf<String, File>()

    init {
        // Подписываемся на запросы чанков от других узлов
        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (type == "FILE_CHUNK_REQ") {
                handleChunkRequest(data, fromIp)
            }
        }
    }

    /**
     * РЕЖИМ СЕРВЕРА: Отправка куска файла по запросу
     */
    private fun handleChunkRequest(jsonData: String, targetIp: String) {
        scope.launch {
            val json = JSONObject(jsonData)
            val fileId = json.getString("file_id")
            val chunkIndex = json.getInt("chunk_index")
            val file = seedingFiles[fileId] ?: return@launch

            val raf = RandomAccessFile(file, "r")
            val pos = chunkIndex.toLong() * CHUNK_SIZE
            if (pos < file.length()) {
                raf.seek(pos)
                val buffer = ByteArray(CHUNK_SIZE)
                val bytesRead = raf.read(buffer)
                
                // Отправляем чанк обратно
                val response = JSONObject().apply {
                    put("type", "FILE_CHUNK_DATA")
                    put("file_id", fileId)
                    put("chunk_index", chunkIndex)
                    put("data", android.util.Base64.encodeToString(buffer, 0, bytesRead, android.util.Base64.NO_WRAP))
                }.toString()
                
                identityRepo.sendToSingleAddress(targetIp, response)
            }
            raf.close()
        }
    }

    /**
     * РЕЖИМ КЛИЕНТА: Скачивание файла по частям
     */
    suspend fun downloadFileP2P(targetIp: String, fileId: String, fileName: String, totalChunks: Int): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val raf = RandomAccessFile(outputFile, "rw")
        raf.setLength(totalChunks.toLong() * CHUNK_SIZE)

        for (i in 0 until totalChunks) {
            // Запрашиваем чанк
            val request = JSONObject().apply {
                put("file_id", fileId)
                put("chunk_index", i)
            }.toString()
            
            identityRepo.sendSignalingData(targetIp, "FILE_CHUNK_REQ", request)
            
            // В реальной системе здесь нужен механизм ожидания ответа и повтора при потере UDP пакета
            delay(50) 
        }
        
        raf.close()
        return@withContext outputFile
    }

    fun addFileToSeeding(file: File): String {
        val id = java.util.UUID.randomUUID().toString()
        seedingFiles[id] = file
        return id
    }
}
