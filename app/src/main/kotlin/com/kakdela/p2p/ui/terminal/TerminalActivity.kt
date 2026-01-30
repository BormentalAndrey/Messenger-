private fun showNeonPlaceholder() {
    // Убираем TerminalView временно, чтобы не мешал
    mTerminalView.visibility = View.GONE

    // Создаём overlay-панель прямо в коде
    val placeholder = LinearLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(0xFF0A0015.toInt())  // очень тёмный фиолетовый
        id = View.generateViewId()
        tag = "neon_placeholder"
    }

    // Главный текст "Как дела?"
    val mainText = TextView(this).apply {
        text = "Как дела?"
        textSize = 68f
        setTextColor(0xFF00F5FF.toInt())          // яркий циан
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.12f
        setShadowLayer(24f, 0f, 0f, 0xFF00F5FF.toInt())
        elevation = 16f
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
    }

    // Маленький подтекст снизу
    val subText = TextView(this).apply {
        text = "Терминал разрабатывается…"
        textSize = 24f
        setTextColor(0xFF39FF14.toInt())          // кислотный зелёный
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.08f
        setShadowLayer(14f, 0f, 0f, 0xFF39FF14.toInt())
        elevation = 10f
        setPadding(0, 48, 0, 0)
    }

    // Пульсирующие точки
    val pulse = TextView(this).apply {
        text = "●  ●  ●"
        textSize = 54f
        setTextColor(0xFFFF00D4.toInt())          // неоновый розовый/пурпурный
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.4f
        alpha = 0.85f
        setPadding(0, 72, 0, 0)
    }

    placeholder.addView(mainText)
    placeholder.addView(subText)
    placeholder.addView(pulse)

    // Добавляем в корневой контейнер (предполагается, что у activity_termux корень — FrameLayout или DrawerLayout)
    (findViewById<ViewGroup>(android.R.id.content)).addView(placeholder)

    // Простая пульсация (fade in/out)
    val animator = ObjectAnimator.ofFloat(pulse, "alpha", 0.4f, 1f, 0.4f)
    animator.duration = 2200
    animator.repeatCount = ObjectAnimator.INFINITE
    animator.interpolator = AccelerateDecelerateInterpolator()
    animator.start()

    // Сохраняем ссылку, чтобы потом убрать
    placeholder.tag = "neon_placeholder"
}

// ---------------------------------------------------------------------------------------------

// Пример использования в onCreate или после неудачной инициализации

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_termux)

    // ... ваши findViewById ...

    // Показываем заглушку сразу
    showNeonPlaceholder()

    TermuxInstaller.setupBootstrapIfNeeded(this) { success ->
        runOnUiThread {
            if (success) {
                // Убираем заглушку
                findViewById<View?>(android.R.id.content)
                    ?.findViewWithTag<View>("neon_placeholder")
                    ?.let { placeholder ->
                        (placeholder.parent as? ViewGroup)?.removeView(placeholder)
                    }

                mTerminalView.visibility = View.VISIBLE
                setupTerminalSession()
            } else {
                // Можно поменять текст на ошибку
                // но для этого нужно сохранить ссылки на TextView-ы
            }
        }
    }
}
