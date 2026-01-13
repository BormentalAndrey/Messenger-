package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
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

    // =========================
    // INITIALIZATION
    // =========================
    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                val appContext = context.applicationContext

                webView = WebView(appContext).apply {

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = userAgentString.replace("; wv", "")
                        allowFileAccess = false
                        allowContentAccess = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null && url.startsWith(BASE_URL)) {
                                isReady.set(true)
                                Log.i(TAG, "WebView READY: $url")
                            } else {
                                Log.w(TAG, "Unexpected page: $url")
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            Log.d("WebViewJS", msg?.message() ?: "")
                            return true
                        }
                    }
                }

                // --- Cookies (КРИТИЧНО для InfinityFree) ---
                webView?.let { wv ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(wv, true)
                    }
                }

                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init failed", e)
            }
        }
    }

    // =========================
    // API METHODS
    // =========================
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        return executeRequest("POST", bodyJson)
    }

    suspend fun getAllNodes(): ServerResponse {
        return ServerResponse(success = false, error = "NOT_IMPLEMENTED")
    }

    // =========================
    // CORE REQUEST EXECUTOR
    // =========================
    private suspend fun executeRequest(method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(method, bodyJson)
                }
                val type = object : TypeToken<ServerResponse>() {}.type
                return@withLock gson.fromJson(rawResponse, type)
            } catch (e: Exception) {
                Log.e(TAG, "Request failed (attempt ${attempt + 1})", e)
                isReady.set(false)
                withContext(Dispatchers.Main) {
                    webView?.loadUrl(BASE_URL)
                }
                delay(RETRY_DELAY_MS)
            }
        }
        return@withLock ServerResponse(success = false, error = "MAX_RETRIES_EXCEEDED")
    }

    // =========================
    // WAIT FOR PAGE READY
    // =========================
    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) {
                delay(250)
            }
        }
    }

    // =========================
    // JS FETCH BRIDGE
    // =========================
    private suspend fun performJsFetch(method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->

            val bridgeName = "AndroidBridge_${System.nanoTime()}"

            val escapedJson = bodyJson?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"

            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName)
                if (!cont.isActive) return@WebViewBridge

                result.onSuccess { cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            val js = """
                (function() {
                    try {
                        const raw = '$escapedJson';
                        const payload = raw !== 'null' ? JSON.parse(raw) : null;

                        fetch('$API_URL', {
                            method: '$method',
                            headers: {
                                'Content-Type': 'application/json',
                                'Accept': 'application/json'
                            },
                            credentials: 'include',
                            body: payload ? JSON.stringify(payload) : null
                        })
                        .then(r => r.text())
                        .then(text => {
                            try {
                                JSON.parse(text);
                                $bridgeName.onSuccess(text);
                            } catch (e) {
                                $bridgeName.onError('INVALID_JSON:' + text.substring(0,200));
                            }
                        })
                        .catch(e => $bridgeName.onError(e.toString()));
                    } catch(e) {
                        $bridgeName.onError('JS_EXCEPTION:' + e.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    // =========================
    // CLEANUP
    // =========================
    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.removeAllViews()
                webView?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Destroy failed", e)
            } finally {
                webView = null
                isReady.set(false)
            }
        }
    }
}
