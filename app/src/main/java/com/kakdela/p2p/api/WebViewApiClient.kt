package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
    private const val API_URL = "http://kakdela.infinityfree.me/api.php"

    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
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
                        // Устанавливаем современный UserAgent, чтобы хостинг не считал нас ботом
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            Log.d(TAG, "Page loaded: $url, Cookies present: ${cookies != null}")
                            
                            // InfinityFree требует прохождения проверки JS и получения куки __test
                            if (url != null && url.contains("kakdela")) {
                                if (cookies != null && cookies.contains("__test")) {
                                    isReady.set(true)
                                    Log.i(TAG, "Protection passed. API Ready.")
                                }
                            }
                        }
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView!!, true)
                
                // Загружаем скрипт, чтобы "прогреть" куки защиты
                webView?.loadUrl(API_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        val url = "$API_URL?action=announce" 
        return executeRequest(url, "POST", bodyJson)
    }

    suspend fun getAllNodes(): ServerResponse {
        val url = "$API_URL?action=get_nodes"
        return executeRequest(url, "GET", null)
    }

    private suspend fun executeRequest(url: String, method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(url, method, bodyJson)
                }

                // Если сервер вернул HTML вместо JSON (например, страницу ошибки хостинга)
                if (rawResponse.trim().startsWith("<")) {
                    throw Exception("HTML received instead of JSON. Possible session timeout.")
                }

                return@withLock gson.fromJson(rawResponse, ServerResponse::class.java)

            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                isReady.set(false)
                // Перезагружаем страницу для обновления куки __test
                withContext(Dispatchers.Main) { webView?.loadUrl(API_URL) }
                delay(3000)
            }
        }
        return@withLock ServerResponse(success = false, error = "Connection failed after $MAX_RETRIES retries")
    }

    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) delay(500)
        }
    }

    private suspend fun performJsFetch(url: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val bridgeName = "JSBridge_${System.currentTimeMillis()}"

            val bridge = object {
                @JavascriptInterface
                fun onSuccess(result: String) {
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }

                @JavascriptInterface
                fun onError(error: String) {
                    cleanup()
                    if (cont.isActive) cont.resumeWithException(Exception(error))
                }

                private fun cleanup() {
                    Handler(Looper.getMainLooper()).post {
                        webView?.removeJavascriptInterface(bridgeName)
                    }
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            // Используем Base64 для безопасной передачи JSON в JavaScript
            val base64Data = bodyJson?.let { 
                Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) 
            } ?: ""

            

            val js = """
                (function() {
                    try {
                        let bodyStr = null;
                        const b64 = '$base64Data';
                        if (b64 !== '') {
                            // Декодируем Base64 обратно в строку корректно (поддержка UTF-8)
                            bodyStr = decodeURIComponent(escape(window.atob(b64)));
                        }

                        fetch('$url', {
                            method: '$method',
                            headers: { 
                                'Content-Type': 'application/json',
                                'Accept': 'application/json'
                            },
                            credentials: 'include',
                            body: bodyStr
                        })
                        .then(r => {
                            if(!r.ok) throw new Error('HTTP Status ' + r.status);
                            return r.text();
                        })
                        .then(t => { window.$bridgeName.onSuccess(t); })
                        .catch(e => { window.$bridgeName.onError(e.toString()); });
                    } catch(e) { window.$bridgeName.onError('JS Error: ' + e.toString()); }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
        }
    }
}
