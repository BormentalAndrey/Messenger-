package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets



@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, title: String, navController: NavHostController) {
    // Декодируем URL (важно для ссылок с параметрами)
    val decodedUrl = try {
        URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) { url }

    // Определяем, что это TikTok
    val isTikTok = decodedUrl.contains("tiktok.com", ignoreCase = true)

    // Ссылка на WebView для обработки кнопки Назад
    var webView: WebView? by remember { mutableStateOf(null) }

    // Обработка системной кнопки "Назад"
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            navController.popBackStack()
        }
    }

    // Мы убрали Scaffold и TopAppBar, чтобы контент был на весь экран
    // Нижняя панель (BottomBar) останется, так как она управляется в NavGraph
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false

                    // Десктопный User-Agent ТОЛЬКО для TikTok
                    if (isTikTok) {
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                }

                // Логика блокировки редиректов (твой код)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        if (!isTikTok) return false

                        val targetUrl = request?.url.toString()
                        if (targetUrl.contains("play.google.com", ignoreCase = true) ||
                            targetUrl.contains("apps.apple.com", ignoreCase = true) ||
                            targetUrl.contains("tiktok.com/download", ignoreCase = true) ||
                            targetUrl.contains("ads.tiktok.com", ignoreCase = true) ||
                            targetUrl.contains("click.tiktok.com", ignoreCase = true) ||
                            targetUrl.contains("app.install", ignoreCase = true)
                        ) {
                            return true
                        }
                        return false
                    }
                }

                // Инжект JS для защиты (твой код)
                if (isTikTok) {
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress == 100) {
                                val blockScript = """
                                    (function() {
                                        window.open = function() { return null; };
                                        const originalAssign = location.assign;
                                        const originalReplace = location.replace;
                                        location.assign = function(url) {
                                            if (url.includes('play.google.com') || url.includes('app')) return;
                                            originalAssign(url);
                                        };
                                        location.replace = function(url) {
                                            if (url.includes('play.google.com') || url.includes('app')) return;
                                            originalReplace(url);
                                        };
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(blockScript, null)
                            }
                        }
                    }
                }

                loadUrl(decodedUrl)
                webView = this
            }
        },
        update = {
            webView = it
        },
        modifier = Modifier.fillMaxSize()
    )
}

