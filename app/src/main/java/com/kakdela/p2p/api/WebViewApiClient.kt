@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val API_URL  = "http://kakdela.infinityfree.me/api.php"
    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 45_000L
    private const val RETRY_DELAY_MS       = 3_000L // Немного увеличим для стабильности
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
                        // Имитируем полноценный браузер
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Проверяем, появилась ли кука __test (метка прохождения защиты)
                            val cookies = CookieManager.getInstance().getCookie(url)
                            if (cookies != null && cookies.contains("__test")) {
                                isReady.set(true)
                                Log.i(TAG, "Protection PASSED, WebView READY")
                            }
                        }
                    }
                }

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView!!, true)
                }

                webView?.loadUrl(BASE_URL)
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
            }
        }
    }

    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        return executeRequest("POST", bodyJson)
    }

    // РЕАЛИЗОВАНО: Теперь можно получать список узлов
    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("GET", null)
    }

    private suspend fun executeRequest(method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(method, bodyJson)
                }
                return@withLock gson.fromJson(rawResponse, ServerResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                isReady.set(false)
                withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
                delay(RETRY_DELAY_MS)
            }
        }
        return@withLock ServerResponse(success = false, error = "MAX_RETRIES_EXCEEDED")
    }

    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) {
                delay(500)
            }
        }
    }

    private suspend fun performJsFetch(method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val escapedJson = bodyJson?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"

            val bridge = object {
                @android.webkit.JavascriptInterface
                fun onSuccess(result: String) {
                    webView?.removeJavascriptInterface(bridgeName)
                    if (cont.isActive) cont.resume(result)
                }
                @android.webkit.JavascriptInterface
                fun onError(error: String) {
                    webView?.removeJavascriptInterface(bridgeName)
                    if (cont.isActive) cont.resumeWithException(Exception(error))
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            // Добавляем ?action=... если нужно, но в нашем api.php мы ориентируемся на метод POST/GET
            val js = """
                (function() {
                    fetch('$API_URL', {
                        method: '$method',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: $method === 'GET' ? null : '$escapedJson'
                    })
                    .then(r => r.text())
                    .then(t => {
                        if (t.includes('<html>')) { 
                            $bridgeName.onError('STILL_BLOCKED_BY_AES'); 
                        } else {
                            $bridgeName.onSuccess(t);
                        }
                    })
                    .catch(e => $bridgeName.onError(e.toString()));
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }
}
