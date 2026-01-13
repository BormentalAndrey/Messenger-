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
import kotlin.coroutines.resumeWithException

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
     * Анонс себя в сети.
     */
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val wrapped = mapOf("data" to payload)
        return executeRequest("add_user", "POST", gson.toJson(wrapped))
    }

    /**
     * Поддержка старой сигнатуры для IdentityRepository.
     */
    suspend fun announceSelf(wrapper: UserRegistrationWrapper): ServerResponse {
        val wrapped = mapOf("data" to wrapper.data)
        return executeRequest("add_user", "POST", gson.toJson(wrapped))
    }

    /**
     * Получение списка всех узлов.
     */
    suspend fun getAllNodes(): ServerResponse = executeRequest("list_users", "GET", null)

    /**
     * Основной исполнитель запросов.
     */
    private suspend fun executeRequest(action: String, method: String, bodyJson: String?): ServerResponse {
        return mutex.withLock {
            var attempt = 0
            while (attempt < 3) {
                try {
                    waitForReady()
                    val resultString = withTimeout(REQUEST_TIMEOUT_MS) { 
                        performJsFetch(action, method, bodyJson) 
                    }
                    
                    val response = gson.fromJson(resultString, ServerResponse::class.java)
                    return response ?: ServerResponse(false, "Parsing failed")
                } catch (e: Exception) {
                    Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                    isReady.set(false)
                    withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
                    delay(2000)
                    attempt++
                }
            }
            ServerResponse(false, "Max retries reached")
        }
    }

    private suspend fun waitForReady() {
        withTimeout(30_000) { while (!isReady.get()) delay(500) }
    }

    /**
     * ИСПРАВЛЕНО: Убрана путаница с типами в fold.
     * Теперь метод четко возвращает String или выбрасывает Exception.
     */
    private suspend fun performJsFetch(action: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<String> { continuation ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"
            val escapedJson = bodyJson?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"
            
            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (continuation.isActive) {
                    // Явно обрабатываем результат без использования неоднозначного fold()
                    try {
                        val successValue = result.getOrThrow()
                        continuation.resume(successValue)
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                }
            }
            
            webView?.addJavascriptInterface(bridge, bridgeName)

            val jsCode = """
                (function() {
                    try {
                        const payload = $escapedJson !== null ? JSON.parse('$escapedJson') : null;
                        fetch('$url', {
                            method: '$method',
                            headers: { 'Content-Type': 'application/json' },
                            body: payload ? JSON.stringify(payload) : null
                        })
                        .then(r => r.text())
                        .then(text => {
                            try { 
                                JSON.parse(text); 
                                $bridgeName.onSuccess(text); 
                            } catch(e) { 
                                $bridgeName.onError('INVALID_JSON: ' + text.substring(0, 100)); 
                            }
                        })
                        .catch(e => $bridgeName.onError(e.toString()));
                    } catch(e) { $bridgeName.onError('JS_EXC: ' + e.toString()); }
                })();
            """.trimIndent()
            webView?.evaluateJavascript(jsCode, null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post { 
            webView?.stopLoading()
            webView?.destroy() 
            webView = null 
            isReady.set(false)
        }
    }
}
