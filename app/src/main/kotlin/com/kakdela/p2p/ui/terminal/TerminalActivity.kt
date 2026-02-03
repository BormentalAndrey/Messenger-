package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.kakdela.p2p.R
import com.termux.app.TermuxInstaller
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : AppCompatActivity(), TerminalSessionClient, TerminalViewClient {

    private lateinit var mTerminalView: TerminalView
    private lateinit var mDrawerLayout: DrawerLayout
    private var mTerminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_termux)

        mTerminalView = findViewById(R.id.terminal_view)
        mDrawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        // Критично: привязываем клиент до любого фокуса/ввода
        mTerminalView.setTerminalViewClient(this)

        mTerminalView.apply {
            setTextSize(35)
            keepScreenOn = true
            // requestFocus() убираем отсюда — перенесём после bootstrap
            setOnClickListener { showSoftKeyboard() }
        }

        settingsButton.setOnClickListener {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.openDrawer(GravityCompat.START)
            } else {
                mDrawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        newSessionButton.setOnClickListener {
            setupTerminalSession()
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            toggleSoftKeyboard()
        }

        // Запускаем bootstrap и только после него — сессию + фокус
        TermuxInstaller.setupBootstrapIfNeeded(this, Runnable {
            TermuxInstaller.setupStorageSymlinks(this)
            setupTerminalSession()
            // Только после успешной инициализации
            mTerminalView.requestFocus()
            mTerminalView.postDelayed({
                showSoftKeyboard()
            }, 400)
        })
    }

    private fun setupTerminalSession() {
        mTerminalSession?.finishIfRunning()
        mTerminalSession = null

        try {
            val prefixDir = TermuxConstants.TERMUX_PREFIX_DIR.absolutePath
            val homeDir = TermuxConstants.TERMUX_HOME_DIR.absolutePath

            val tmpDir = File(prefixDir, "tmp").absolutePath

            File(homeDir).mkdirs()
            File(tmpDir).mkdirs()

            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=$homeDir",
                "PREFIX=$prefixDir",
                "TMPDIR=$tmpDir",
                "PATH=$prefixDir/bin:$prefixDir/bin/applets",
                "LD_LIBRARY_PATH=$prefixDir/lib",
                "LANG=en_US.UTF-8",
                "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
                "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}"
            )

            val shellPath = when {
                File("$prefixDir/bin/bash").canExecute() -> "$prefixDir/bin/bash"
                File("$prefixDir/bin/sh").canExecute() -> "$prefixDir/bin/sh"
                else -> "/system/bin/sh"
            }

            val session = TerminalSession(
                shellPath,
                homeDir,
                arrayOf("-l"),
                env,
                2000,
                this  // TerminalSessionClient
            )

            mTerminalSession = session
            mTerminalView.attachSession(session)
            mTerminalView.onScreenUpdated()

            // showSoftKeyboard перенесён в колбэк после bootstrap

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to setup terminal session", e)
        }
    }

    private fun showSoftKeyboard() {
        if (!mTerminalView.hasFocus()) {
            mTerminalView.requestFocus()
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(mTerminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isAcceptingText) {
            imm.hideSoftInputFromWindow(mTerminalView.windowToken, 0)
        } else {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
    }

    // ───────────────────────────────────────────────
    // TerminalSessionClient методы (без изменений)
    // ───────────────────────────────────────────────

    override fun onTextChanged(session: TerminalSession) {
        mTerminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {}

    override fun onSessionFinished(session: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Termux", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            mTerminalSession?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    // Логирование
    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: TAG, message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: TAG, message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: TAG, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: TAG, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: TAG, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: TAG, "Stack trace", e)
    }

    // ───────────────────────────────────────────────
    // TerminalViewClient методы — полная реализация
    // ───────────────────────────────────────────────

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? {
        // Если сессия ещё не готова — не даём клавиатуре появиться слишком рано
        if (mTerminalSession == null) {
            return null
        }
        // Делегируем стандартной реализации TerminalView
        return mTerminalView.onCreateInputConnection(outAttrs)
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean {
        // Здесь можно добавить обработку специальных клавиш, если нужно
        return false // по умолчанию не обрабатываем — пусть TerminalView сам
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent?): Boolean {
        // Стандартное поведение Termux — копирование/вставка по long press
        return false // false = позволить Termux обработать
    }

    override fun onScale(scale: Float, focusX: Float, focusY: Float): Boolean {
        // Масштабирование (pinch-to-zoom)
        return false // позволить Termux
    }

    override fun onSingleTapUp(event: MotionEvent?): Boolean {
        // Тап по экрану — можно показать клавиатуру
        showSoftKeyboard()
        return true
    }

    override fun shouldCopyOnLongPress(): Boolean = true

    override fun shouldPasteOnLongPress(): Boolean = true

    override fun shouldEnqueueInputOnSoftwareKeyboard(): Boolean = true

    override fun isTerminalViewScalingDisabled(): Boolean = false  // разрешить pinch-zoom

    // Жизненный цикл
    override fun onResume() {
        super.onResume()
        mTerminalView.onScreenUpdated()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mTerminalSession?.finishIfRunning()
    }
}
