package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, title: String, navController: NavHostController) {
    val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())

    // Определяем, что это TikTok
    val isTikTok = decodedUrl.contains("tiktok.com", ignoreCase = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                            // Для остальных сайтов остаётся дефолтный мобильный User-Agent
                        }

                        // WebViewClient с блокировкой редиректов ТОЛЬКО для TikTok
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                if (!isTikTok) return false  // Для не-TikTok — обычное поведение

                                val targetUrl = request?.url.toString()

                                if (targetUrl.contains("play.google.com", ignoreCase = true) ||
                                    targetUrl.contains("apps.apple.com", ignoreCase = true) ||
                                    targetUrl.contains("tiktok.com/download", ignoreCase = true) ||
                                    targetUrl.contains("ads.tiktok.com", ignoreCase = true) ||
                                    targetUrl.contains("click.tiktok.com", ignoreCase = true) ||
                                    targetUrl.contains("app.install", ignoreCase = true)
                                ) {
                                    return true  // Блокируем переход
                                }

                                return false
                            }
                        }

                        // Инжект JS для защиты от авто-кликов и редиректов ТОЛЬКО для TikTok
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
                                                
                                                document.addEventListener('click', function(e) {
                                                    if (!e.isTrusted) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                    }
                                                }, true);
                                            })();
                                        """.trimIndent()

                                        view?.evaluateJavascript(blockScript, null)
                                    }
                                }
                            }
                        }
                        // Если не TikTok — можно оставить дефолтный webChromeClient или null

                        loadUrl(decodedUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
