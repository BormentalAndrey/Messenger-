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

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("WebViewConsole", "[JS] ${consoleMessage?.message()}")
                            return true
                        }
                    }
                }
                
                Log.i(TAG, "Starting Anti-Bot Warmup...")
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "Error init WebView: ${e.message}")
            }
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.clearHistory()
                webView?.removeAllViews()
                webView?.destroy()
                webView = null
                isReady.set(false)
                Log.d(TAG, "WebView destroyed")
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView: ${e.message}")
            }
        }
    }

    /**
     * ИСПРАВЛЕНО: Обертка в "data" для соответствия PHP скрипту
     */
    suspend fun announceSelf(wrapper: UserRegistrationWrapper): ServerResponse {
        val wrappedMap = mapOf("data" to wrapper)
        val jsonBody = gson.toJson(wrappedMap)
        Log.d(TAG, "Sending announce: $jsonBody")
        return executeRequest("add_user", "POST", jsonBody)
    }

    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("list_users", "GET", null)
    }

    private suspend fun executeRequest(
        action: String,
        method: String,
        bodyJson: String?
    ): ServerResponse {
        return mutex.withLock {
            var attempt = 0
            while (attempt < 3) {
                try {
                    waitForReady()

                    val jsonResult = withTimeout(REQUEST_TIMEOUT_MS) {
                        performJsFetch(action, method, bodyJson)
                    }
                    
                    return gson.fromJson(jsonResult, ServerResponse::class.java)

                } catch (e: Exception) {
                    Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                    isReady.set(false)
                    withContext(Dispatchers.Main) {
                        webView?.loadUrl(BASE_URL)
                    }
                    attempt++
                    kotlinx.coroutines.delay(2000)
                }
            }
            return ServerResponse(success = false, error = "connection_failed_after_retries")
        }
    }

    private suspend fun waitForReady() {
        if (isReady.get()) return
        try {
            withTimeout(30_000) {
                while (!isReady.get()) {
                    kotlinx.coroutines.delay(500)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wait for ready timeout. Proceeding anyway.")
        }
    }

    /**
     * Внедряет JS код fetch.
     * ИСПРАВЛЕНО: Теперь bodyJson передается как строка, которая парсится внутри JS,
     * что исключает ошибки синтаксиса при передаче объектов.
     */
    private suspend fun performJsFetch(
        action: String,
        method: String,
        bodyJson: String?
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"
            
            // Защищенная передача строки JSON
            val safeJson = bodyJson?.replace("'", "\\'") ?: "null"
            
            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (continuation.isActive) {
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
                        const url = '$url';
                        const method = '$method';
                        const rawInput = '$safeJson';
                        
                        // Если есть входные данные, парсим их, иначе null
                        const bodyData = (rawInput !== 'null') ? JSON.parse(rawInput) : null;

                        fetch(url, {
                            method: method,
                            headers: {
                                'Content-Type': 'application/json',
                                'Accept': 'application/json'
                            },
                            body: bodyData ? JSON.stringify(bodyData) : null
                        })
                        .then(response => response.text())
                        .then(text => {
                            try {
                                JSON.parse(text);
                                $bridgeName.onSuccess(text);
                            } catch(e) {
                                console.error('Raw response:', text);
                                $bridgeName.onError('INVALID_JSON_HTML_DETECTED');
                            }
                        })
                        .catch(err => {
                            $bridgeName.onError(err.toString());
                        });
                    } catch (e) {
                        $bridgeName.onError('SCRIPT_ERROR: ' + e.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(jsCode, null)
        }
    }
}
