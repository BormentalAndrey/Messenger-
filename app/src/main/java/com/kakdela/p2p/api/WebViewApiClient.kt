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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false 
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
     * Анонс узла. Принимает UserPayload.
     * Оборачивает данные в ключ "data" для корректной работы api.php.
     */
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val wrapped = mapOf("data" to payload)
        val jsonBody = gson.toJson(wrapped)
        return executeRequest("add_user", "POST", jsonBody)
    }

    /**
     * Перегрузка для поддержки старого кода, использующего Wrapper.
     */
    suspend fun announceSelf(wrapper: UserRegistrationWrapper): ServerResponse {
        val wrapped = mapOf("data" to wrapper.data)
        val jsonBody = gson.toJson(wrapped)
        return executeRequest("add_user", "POST", jsonBody)
    }

    /**
     * Получение списка всех узлов.
     */
    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("list_users", "GET", null)
    }

    /**
     * Основной метод выполнения запроса через WebView.
     * Гарантирует возврат ServerResponse, а не сырой строки.
     */
    private suspend fun executeRequest(action: String, method: String, bodyJson: String?): ServerResponse {
        return mutex.withLock {
            var attempt = 0
            while (attempt < 3) {
                try {
                    waitForReady()
                    
                    // Выполняем JS и получаем строку
                    val rawResult: String = withTimeout(REQUEST_TIMEOUT_MS) { 
                        performJsFetch(action, method, bodyJson) 
                    }
                    
                    // Десериализуем строку в объект ответа
                    val response = gson.fromJson(rawResult, ServerResponse::class.java)
                    
                    if (response != null) {
                        return@withLock response
                    } else {
                        throw Exception("Failed to parse server response")
                    }
                    
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
        withTimeout(30_000) { 
            while (!isReady.get()) {
                delay(500) 
            }
        }
    }

    /**
     * Низкоуровневый метод взаимодействия с JavaScript.
     * Возвращает String (JSON).
     */
    private suspend fun performJsFetch(action: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"
            
            val escapedJson = bodyJson?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"
            
            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (continuation.isActive) {
                    // Результат fold возвращает String в случае успеха
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWith(Result.failure(it)) }
                    )
                }
            }
            webView?.addJavascriptInterface(bridge, bridgeName)

            val jsCode = """
                (function() {
                    try {
                        const method = '$method';
                        const url = '$url';
                        const rawJson = '$escapedJson';
                        const payload = rawJson !== 'null' ? JSON.parse(rawJson) : null;

                        fetch(url, {
                            method: method,
                            headers: { 
                                'Content-Type': 'application/json',
                                'Accept': 'application/json'
                            },
                            body: payload ? JSON.stringify(payload) : null
                        })
                        .then(r => r.text())
                        .then(text => {
                            try { 
                                JSON.parse(text); 
                                $bridgeName.onSuccess(text); 
                            } catch(e) { 
                                $bridgeName.onError('NOT_JSON_RESPONSE: ' + text.substring(0, 200)); 
                            }
                        })
                        .catch(e => $bridgeName.onError(e.toString()));
                    } catch(e) { $bridgeName.onError('JS_EXCEPTION: ' + e.toString()); }
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
