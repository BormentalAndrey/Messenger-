package com.kakdela.p2p

import android.app.Application
import android.util.Log
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyApplication : Application() {

    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // 0. PDFBox (ОБЯЗАТЕЛЬНО до работы с PDF)
            PDFBoxResourceLoader.init(this)

            // 1. WebView API (антибот + fetch)
            WebViewApiClient.init(this)

            // 2. Crypto
            CryptoManager.init(this)

            // 3. Identity
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

    override fun onTerminate() {
        WebViewApiClient.destroy()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
