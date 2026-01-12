package com.kakdela.p2p.api

import android.webkit.JavascriptInterface

/**
 * Интерфейс для получения ответов от JavaScript внутри WebView.
 * JS вызывает эти методы, чтобы вернуть результат в Kotlin.
 */
class WebViewBridge(private val callback: (Result<String>) -> Unit) {

    @JavascriptInterface
    fun onSuccess(json: String) {
        callback(Result.success(json))
    }

    @JavascriptInterface
    fun onError(error: String) {
        callback(Result.failure(Exception(error)))
    }
}

