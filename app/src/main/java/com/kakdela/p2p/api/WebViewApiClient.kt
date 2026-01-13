package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private const val API_URL  = "http://kakdela.infinityfree.me/api.php"

    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 45_000L
    private const val RETRY_DELAY_MS       = 2_000L
    private const val MAX_RETRIES          = 3

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
                            if (url?.contains("kakdela.infinityfree.me") == true) {
                                isReady.set(true)
                            }
                            Log.d(TAG, "Loaded: $url")
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            Log.d("WebViewJS", msg?.message() ?: "")
                            return true
                        }
                    }
                }

                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
            }
        }
    }

    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val body = gson.toJson(mapOf("data" to payload))
        return executeRequest("add_user", "POST", body)
    }

    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("list_users", "GET", null)
    }

    private suspend fun executeRequest(
        action: String,
        method: String,
        bodyJson: String?
    ): ServerResponse = mutex.withLock {

        repeat(MAX_RETRIES) {
            try {
                waitForReady()

                val json = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(action, method, bodyJson)
                }

                val type = object : TypeToken<ServerResponse>() {}.type
                return gson.fromJson(json, type)

            } catch (e: Exception) {
                Log.e(TAG, "Request failed", e)
                isReady.set(false)

                withContext(Dispatchers.Main) {
                    webView?.loadUrl(BASE_URL)
                }

                delay(RETRY_DELAY_MS)
            }
        }

        ServerResponse(
            success = false,
            data = null,
            message = "Max retries reached"
        )
    }

    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) delay(300)
        }
    }

    private suspend fun performJsFetch(
        action: String,
        method: String,
        bodyJson: String?
    ): String = withContext(Dispatchers.Main) {

        suspendCancellableCoroutine { cont ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"

            val escapedJson = bodyJson
                ?.replace("\\", "\\\\")
                ?.replace("'", "\\'")
                ?: "null"

            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (!cont.isActive) return@WebViewBridge

                try {
                    cont.resume(result.getOrThrow())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            val js = """
                (function() {
                    try {
                        const raw = '$escapedJson';
                        const payload = raw !== 'null' ? JSON.parse(raw) : null;

                        fetch('$url', {
                            method: '$method',
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
                                $bridgeName.onError('NOT_JSON: ' + text.substring(0, 200));
                            }
                        })
                        .catch(e => $bridgeName.onError(e.toString()));
                    } catch(e) {
                        $bridgeName.onError('JS_EXCEPTION: ' + e.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } finally {
                webView = null
                isReady.set(false)
            }
        }
    }
}
