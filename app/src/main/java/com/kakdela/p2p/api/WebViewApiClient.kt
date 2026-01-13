package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val API_URL = "http://kakdela.infinityfree.me/api.php"
    private const val REQUEST_TIMEOUT_MS = 45_000L 

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    fun init(context: Context) {
        if (webView != null) return
        Handler(Looper.getMainLooper()).post {
            try {
                webView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = userAgentString.replace("; wv", "")
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "Page loaded: $url")
                            if (url?.contains("kakdela.infinityfree.me") == true) {
                                isReady.set(true)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            Log.d("WebViewConsole", "[JS] ${msg?.message()}")
                            return true
                        }
                    }
                }
                webView?.loadUrl(BASE_URL)
            } catch (e: Exception) { Log.e(TAG, "Init Error: ${e.message}") }
        }
    }

    /**
     * Исправлено: Оборачиваем в "data" ТУТ, чтобы PHP (api.php:27) увидел ключ.
     */
    suspend fun announceSelf(wrapper: UserRegistrationWrapper): ServerResponse {
        val wrapped = mapOf("data" to wrapper)
        val jsonBody = gson.toJson(wrapped)
        return executeRequest("add_user", "POST", jsonBody)
    }

    suspend fun getAllNodes(): ServerResponse = executeRequest("list_users", "GET", null)

    private suspend fun executeRequest(action: String, method: String, bodyJson: String?): ServerResponse {
        return mutex.withLock {
            var attempt = 0
            while (attempt < 3) {
                try {
                    waitForReady()
                    val result = withTimeout(REQUEST_TIMEOUT_MS) { performJsFetch(action, method, bodyJson) }
                    return gson.fromJson(result, ServerResponse::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                    isReady.set(false)
                    withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
                    delay(2000)
                    attempt++
                }
            }
            return ServerResponse(false, "Max retries reached")
        }
    }

    private suspend fun waitForReady() {
        withTimeout(30_000) { while (!isReady.get()) delay(500) }
    }

    private suspend fun performJsFetch(action: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"
            
            // Важно: экранируем только обратные слеши и одинарные кавычки для JS строки
            val escapedJson = bodyJson?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"
            
            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (continuation.isActive) {
                    result.fold({ continuation.resume(it) }, { continuation.resumeWith(Result.failure(it)) })
                }
            }
            webView?.addJavascriptInterface(bridge, bridgeName)

            val jsCode = """
                (function() {
                    try {
                        const method = '$method';
                        const url = '$url';
                        const rawJson = '$escapedJson';
                        
                        // Парсим строку в объект, чтобы fetch отправил чистый JSON
                        const payload = rawJson !== 'null' ? JSON.parse(rawJson) : null;

                        fetch(url, {
                            method: method,
                            headers: { 'Content-Type': 'application/json' },
                            body: payload ? JSON.stringify(payload) : null
                        })
                        .then(r => r.text())
                        .then(text => {
                            try { JSON.parse(text); $bridgeName.onSuccess(text); }
                            catch(e) { $bridgeName.onError('NOT_JSON: ' + text); }
                        })
                        .catch(e => $bridgeName.onError(e.toString()));
                    } catch(e) { $bridgeName.onError('JS_EXC: ' + e.toString()); }
                })();
            """.trimIndent()
            webView?.evaluateJavascript(jsCode, null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post { webView?.destroy(); webView = null }
    }
}
