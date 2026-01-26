package com.kakdela.p2p.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat

class BrowserActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var progressBar: ProgressBar
    
    // Кнопки навигации
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var refreshButton: TextView
    private lateinit var homeButton: TextView
    private lateinit var goButton: TextView

    // Цветовая палитра Neon / Cyberpunk
    private val colorBg = Color.parseColor("#121212") // Глубокий черный
    private val colorSurface = Color.parseColor("#1E1E1E") // Чуть светлее
    private val colorNeonCyan = Color.parseColor("#00E5FF") // Неоновый голубой
    private val colorNeonPink = Color.parseColor("#FF4081") // Неоновый розовый
    private val colorText = Color.WHITE
    private val colorTextHint = Color.parseColor("#80FFFFFF")

    private val homeUrl = "https://www.google.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Настройка окна и статус бара (черный цвет)
        window.statusBarColor = colorBg
        window.navigationBarColor = colorBg

        // --- КОРНЕВОЙ LAYOUT ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colorBg)
        }

        // --- ВЕРХНЯЯ ПАНЕЛЬ (Top Bar) ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            val p = dpToPx(12)
            setPadding(p, p, p, p)
            background = createBackgroundDrawable(colorSurface, 0f, 0, 0) // Плоский фон
            elevation = dpToPx(4).toFloat()
        }

        // Поле ввода URL
        urlEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f).apply {
                marginEnd = dpToPx(8)
            }
            hint = "Search or enter URL..."
            setHintTextColor(colorTextHint)
            setTextColor(colorText)
            textSize = 14f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            // Неоновый стиль для инпута
            background = createBackgroundDrawable(Color.parseColor("#2C2C2C"), dpToPx(22).toFloat(), 2, colorNeonCyan)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
        }

        // Кнопка GO (вместо обновить в верхнем баре, так логичнее)
        goButton = createNeonButton("➜", colorNeonPink).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
            setOnClickListener { processUrlInput() }
        }

        topBar.addView(urlEditText)
        topBar.addView(goButton)

        // --- ПРОГРЕСС БАР ---
        // Стильный тонкий прогресс бар прямо под тулбаром
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(3)).apply {
                topMargin = -dpToPx(3) // Наложение
            }
            max = 100
            progressTintList = ColorStateList.valueOf(colorNeonPink) // Розовый прогресс
            visibility = View.GONE
        }

        // --- WEBVIEW ---
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            id = ViewCompat.generateViewId()
            setBackgroundColor(colorBg) // Чтобы не мигало белым при загрузке
        }

        // --- НИЖНЯЯ ПАНЕЛЬ (Bottom Bar) ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                dpToPx(56) // Фиксированная высота для удобства нажатия
            )
            gravity = Gravity.CENTER
            setBackgroundColor(colorSurface)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        // Создание кнопок с Unicode иконками
        backButton = createNavButton("❮", colorNeonCyan)
        forwardButton = createNavButton("❯", colorNeonCyan)
        refreshButton = createNavButton("↻", colorNeonPink) // Refresh выделен цветом
        homeButton = createNavButton("⌂", colorNeonCyan) // Домик

        // Распределение кнопок равномерно
        val spacerParams = LinearLayout.LayoutParams(0, 1, 1f)
        
        bottomBar.addView(backButton)
        bottomBar.addView(View(this).apply { layoutParams = spacerParams })
        bottomBar.addView(forwardButton)
        bottomBar.addView(View(this).apply { layoutParams = spacerParams })
        bottomBar.addView(refreshButton)
        bottomBar.addView(View(this).apply { layoutParams = spacerParams })
        bottomBar.addView(homeButton)

        // Сборка Layout
        rootLayout.addView(topBar)
        rootLayout.addView(progressBar)
        rootLayout.addView(webView)
        rootLayout.addView(bottomBar)

        setContentView(rootLayout)

        // --- НАСТРОЙКИ WEBVIEW ---
        initWebViewSettings()

        // --- ЛОГИКА ---
        loadUrl(homeUrl)

        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                processUrlInput()
                true
            } else false
        }

        // Обработчики нажатий
        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        forwardButton.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        refreshButton.setOnClickListener { webView.reload() }
        homeButton.setOnClickListener { loadUrl(homeUrl) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebViewSettings() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Поддержка смешанного контента (HTTP картинки на HTTPS сайте)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            
            // 🔥 DARK MODE FOR WEB CONTENT
            // Принудительно затемняем веб-страницы, чтобы соответствовать стилю браузера
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_ON
            }
        }

        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyWebChromeClient()
    }

    private fun processUrlInput() {
        val input = urlEditText.text.toString().trim()
        if (input.isEmpty()) return

        hideKeyboard()
        
        // Простая проверка: это URL или поисковый запрос?
        if (Patterns.WEB_URL.matcher(input).matches() || input.contains(".") && !input.contains(" ")) {
            loadUrl(input)
        } else {
            // Если это не URL, ищем в Google
            loadUrl("https://www.google.com/search?q=$input")
        }
    }

    private fun loadUrl(url: String) {
        var formattedUrl = url
        // Добавляем протокол, если его нет
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            formattedUrl = "https://$url"
        }
        webView.loadUrl(formattedUrl)
        // Не меняем текст в поле сразу, ждем onPageStarted
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlEditText.windowToken, 0)
        urlEditText.clearFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- CLIENTS ---

    private inner class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            // Возвращаем false, чтобы WebView сам обрабатывал переходы (стандартное поведение браузера)
            return false 
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            url?.let {
                if (!urlEditText.isFocused) {
                    urlEditText.setText(it)
                }
            }
            updateNavButtons()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            updateNavButtons()
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // Игнорируем ошибки net::ERR_CACHE_MISS и подобные мелкие сбои
            if (error?.errorCode != WebViewClient.ERROR_HOST_LOOKUP) {
               // Можно логировать, но тосты раздражают пользователей
            }
            progressBar.visibility = View.GONE
        }
    }

    private inner class MyWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress == 100) {
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.VISIBLE
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            // Обновляем заголовок активити, если нужно, или просто оставляем название приложения
        }
    }
    
    private fun updateNavButtons() {
        // Меняем прозрачность кнопок, если действие недоступно
        backButton.alpha = if (webView.canGoBack()) 1.0f else 0.3f
        backButton.isEnabled = webView.canGoBack()
        
        forwardButton.alpha = if (webView.canGoForward()) 1.0f else 0.3f
        forwardButton.isEnabled = webView.canGoForward()
    }

    // --- UI HELPER FUNCTIONS (NEON STYLE GENERATORS) ---

    /**
     * Создает кнопку в стиле нижней панели навигации
     */
    private fun createNavButton(textIcon: String, color: Int): TextView {
        return TextView(this).apply {
            text = textIcon
            textSize = 24f
            setTextColor(createColorStateList(color, Color.GRAY))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            // Эффект нажатия (ripple без xml)
            background = getRippleDrawable(color)
        }
    }

    /**
     * Создает основную яркую кнопку (например, GO)
     */
    private fun createNeonButton(text: String, accentColor: Int): Button {
        return Button(this).apply {
            setText(text)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            // Фон: Нормальный = цвет акцента, Нажат = темнее
            val bgNormal = createBackgroundDrawable(accentColor, dpToPx(22).toFloat(), 0, 0)
            val bgPressed = createBackgroundDrawable(darkenColor(accentColor), dpToPx(22).toFloat(), 0, 0)
            
            val stateList = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), bgPressed)
                addState(intArrayOf(), bgNormal)
            }
            background = stateList
            elevation = dpToPx(4).toFloat()
        }
    }

    /**
     * Генерирует Drawable с закругленными углами и обводкой
     */
    private fun createBackgroundDrawable(fillColor: Int, radius: Float, strokeWidth: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }
    
    /**
     * Эффект нажатия для текстовых кнопок
     */
    private fun getRippleDrawable(color: Int): StateListDrawable {
        val drawable = StateListDrawable()
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)))
        }
        drawable.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        drawable.addState(intArrayOf(), null)
        return drawable
    }

    private fun createColorStateList(normal: Int, pressed: Int): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(pressed, normal)
        )
    }
    
    private fun darkenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.8f // Темнее на 20%
        return Color.HSVToColor(hsv)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
