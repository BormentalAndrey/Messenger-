package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
// ВАЖНО: Проверь, что в твоем модуле :termux-library пути именно такие. 
// Если будет ошибка "Unresolved reference", просто удали эти импорты и нажми Alt+Enter.
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : AppCompatActivity(),
    TerminalViewClient,
    TerminalSessionClient {

    private lateinit var terminalView: TerminalView
    private lateinit var terminalSession: TerminalSession

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация UI компонентов
        val root = FrameLayout(this)
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Инициализация View терминала
        terminalView = TerminalView(this, null)
        terminalView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        terminalView.setTerminalViewClient(this)
        root.addView(terminalView)
        setContentView(root)

        // Подготовка файловой системы для работы терминала
        val homeDir = File(filesDir, "home").apply { if (!exists()) mkdirs() }
        val usrBin = File(filesDir, "usr/bin").apply { if (!exists()) mkdirs() }
        val tmpDir = File(cacheDir, "tmp").apply { if (!exists()) mkdirs() }

        // Настройка окружения (Environment)
        // Для P2P мессенджера это важно, чтобы скрипты понимали, где они находятся
        val env = arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PWD=${homeDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "PATH=/system/bin:/system/xbin:${usrBin.absolutePath}",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=/system/lib64:/system/lib"
        )

        // Создание сессии терминала
        // "/system/bin/sh" — стандартная оболочка Android. 
        // Если ты распаковал bootstrap Termux, путь может быть другим.
        try {
            terminalSession = TerminalSession(
                "/system/bin/sh",
                homeDir.absolutePath,
                arrayOf("-l"), // -l означает login shell
                env,
                1000, // Лимит строк прокрутки
                this
            )

            // Привязка сессии к View
            terminalView.attachSession(terminalSession)
        } catch (e: Exception) {
            logError("Terminal", "Failed to start terminal session: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        // Автоматический фокус на терминал при открытии экрана
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        // Обязательное завершение сессии для освобождения ресурсов
        if (::terminalSession.isInitialized) {
            terminalSession.finishIfRunning()
        }
        super.onDestroy()
    }

    // ================= TerminalViewClient Impl =================
    // Эти методы управляют взаимодействием пользователя с экраном терминала

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) {
        // При клике показываем клавиатуру, если она скрыта
        terminalView.requestFocus()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && session.isRunning) {
            // Если нужно, чтобы кнопка "Назад" не закрывала Activity, а шла в консоль
            return false 
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (session.isRunning) {
            session.writeCodePoint(ctrlDown, codePoint)
            return true
        }
        return false
    }

    override fun onEmulatorSet() {
        // Вызывается, когда эмулятор терминала готов к работе
    }

    // Логирование через систему Termux
    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "Terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "Terminal", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Terminal", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Terminal", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Terminal", message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: "Terminal", message ?: "", e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "Terminal", "stacktrace", e) }

    // ================= TerminalSessionClient Impl =================
    // Эти методы обрабатывают события внутри самой консоли

    override fun onTextChanged(session: TerminalSession) {
        // Перерисовываем экран при получении новых данных
        terminalView.invalidate()
    }

    override fun onTitleChanged(session: TerminalSession) {
        // Можно выводить заголовок окна (например, имя текущего процесса)
        title = session.title
    }

    override fun onSessionFinished(session: TerminalSession) {
        // Закрываем Activity, если пользователь ввел 'exit' в консоли
        if (!isFinishing) finish()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        // Реализация копирования из терминала (можно добавить ClipboardManager)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        // Реализация вставки в терминал
    }

    override fun onBell(session: TerminalSession) {
        // Системный "писк" или вибрация
    }

    override fun onColorsChanged(session: TerminalSession) {
        terminalView.invalidate()
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        logInfo("Terminal", "Shell started with PID: $pid")
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // Обработка мигания курсора
    }

    override fun getTerminalCursorStyle(): Int = 0 // 0 = BLOCK, 1 = BAR, 2 = UNDERLINE
    }
    
