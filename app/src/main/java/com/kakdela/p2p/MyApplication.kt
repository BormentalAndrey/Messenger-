package com.kakdela.p2p

import android.app.Application
import android.util.Log
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

class MyApplication : Application() {

    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // 0. Инициализация папок терминала (usr, home, tmp)
            // Это гарантирует, что папки будут созданы с правильными правами доступа
            initTerminalFileSystem()

            // 1. PDFBox (ОБЯЗАТЕЛЬНО до работы с PDF)
            PDFBoxResourceLoader.init(this)

            // 2. WebView API (антибот + fetch)
            WebViewApiClient.init(this)

            // 3. Crypto
            CryptoManager.init(this)

            // 4. Identity
            val myId = identityRepository.getMyId()
            if (myId.isNotEmpty()) {
                Log.i(TAG, "Init OK. Peer ID: $myId")
            } else {
                Log.w(TAG, "Peer ID empty — waiting for registration")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical init error", e)
        }
    }

    /**
     * Предварительное создание структуры папок для Termux среды.
     * Это решает проблему "не видит файлы", если система пытается 
     * обратиться к путям до завершения распаковки.
     */
    private fun initTerminalFileSystem() {
        val filesDir = filesDir
        val folders = listOf("usr", "home", "tmp", "usr/bin", "usr/lib")
        
        folders.forEach { path ->
            val dir = File(filesDir, path)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (created) Log.d(TAG, "Created directory: $path")
            }
        }
    }

    override fun onTerminate() {
        WebViewApiClient.destroy()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
