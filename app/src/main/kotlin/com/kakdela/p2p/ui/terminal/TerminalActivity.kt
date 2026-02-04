package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.kakdela.p2p.R
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class TerminalActivity :
    AppCompatActivity(),
    TerminalSessionClient,
    TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private var terminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"
        // Точный тег релиза из вашего скриншота. 
        // Символ '+' в URL ДОЛЖЕН быть заменен на %2B
        private const val RELEASE_TAG = "bootstrap-2024.12.18-r1%2Bapt-android-7"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Установка вашего XML
        setContentView(R.layout.activity_termux)

        // Привязка ID из предоставленного XML
        terminalView = findViewById(R.id.terminal_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(35)
        terminalView.keepScreenOn = true
        
        // Фокус и клавиатура по тапу
        terminalView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                showKeyboard()
            }
            terminalView.onTouchEvent(event)
        }

        settingsButton.setOnClickListener {
            // В вашем XML кнопка настроек внутри Drawer, обычно она вызывает внешнее меню
            // Но здесь мы просто добавим лог для проверки нажатия
            Log.d(TAG, "Settings clicked")
        }

        newSessionButton.setOnClickListener {
            setupSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            toggleKeyboard()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })

        checkAndInstallBootstrap()
    }

    private fun checkAndInstallBootstrap() {
        val prefixDir = File(filesDir, "usr")
        val bashFile = File(prefixDir, "bin/bash")

        if (bashFile.exists() && bashFile.canExecute()) {
            setupSession()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Установка системы")
            .setMessage("Требуется загрузка компонентов Termux. Продолжить?")
            .setPositiveButton("Загрузить") { _, _ ->
                val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                
                val archiveName = when {
                    arch.contains("aarch64") || arch.contains("arm64") -> "bootstrap-aarch64.zip"
                    arch.contains("armeabi") || arch.contains("arm") -> "bootstrap-arm.zip"
                    arch.contains("x86_64") -> "bootstrap-x86_64.zip"
                    arch.contains("i686") || arch.contains("x86") -> "bootstrap-i686.zip"
                    else -> "bootstrap-aarch64.zip"
                }

                val downloadUrl = "https://github.com/termux/termux-packages/releases/download/$RELEASE_TAG/$archiveName"
                startBootstrapDownload(downloadUrl)
            }
            .setNegativeButton("Выход") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startBootstrapDownload(urlStr: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Загрузка")
            setMessage("Скачивание системных файлов...")
            setIndeterminate(true)
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }
                val zipFile = File(tmpDir, "bootstrap.zip")

                // 1. Скачивание с обработкой редиректов
                downloadFile(urlStr, zipFile)

                // 2. Распаковка ZIP
                runOnUiThread { progressDialog.setMessage("Распаковка...") }
                unzipBootstrap(zipFile)

                // 3. Создание симлинков (Критично для работы Bash)
                runOnUiThread { progressDialog.setMessage("Конфигурация...") }
                applySymlinks()

                zipFile.delete()

                runOnUiThread {
                    progressDialog.dismiss()
                    setupSession()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Install error: ${e.message}")
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Ошибка сети")
                        .setMessage("Не удалось скачать файл (404 или нет интернета).\n\nURL: $urlStr")
                        .setPositiveButton("Повторить") { _, _ -> checkAndInstallBootstrap() }
                        .setNegativeButton("Отмена") { _, _ -> finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun downloadFile(urlStr: String, destFile: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Termux-Android-App")
        }

        if (conn.responseCode >= 400) {
            throw Exception("HTTP ${conn.responseCode}")
        }

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun unzipBootstrap(zipFile: File) {
        val rootDir = filesDir
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outputFile = File(rootDir, entry.name)
                
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { zis.copyTo(it) }

                    // Выдача прав на выполнение
                    if (entry.name.contains("bin/") || entry.name.endsWith(".so")) {
                        outputFile.setExecutable(true, false)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinks() {
        val usrDir = File(filesDir, "usr")
        val symlinkFile = File(usrDir, "SYMLINKS.txt")
        if (!symlinkFile.exists()) return

        try {
            symlinkFile.readLines().forEach { line ->
                val parts = line.split("←")
                if (parts.size == 2) {
                    val linkFile = File(usrDir, parts[0])
                    val target = parts[1]
                    linkFile.delete()
                    try {
                        Os.symlink(target, linkFile.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Symlink fail: ${parts[0]}")
                    }
                }
            }
            symlinkFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Symlinks error", e)
        }
    }

    private fun setupSession() {
        try {
            val usrDir = File(filesDir, "usr")
            val homeDir = File(filesDir, "home").apply { if (!exists()) mkdirs() }
            
            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${homeDir.absolutePath}",
                "PREFIX=${usrDir.absolutePath}",
                "PATH=${usrDir.absolutePath}/bin",
                "LD_LIBRARY_PATH=${usrDir.absolutePath}/lib",
                "LANG=en_US.UTF-8"
            )

            val shellPath = File(usrDir, "bin/bash").absolutePath
            
            terminalSession = TerminalSession(shellPath, homeDir.absolutePath, arrayOf("-l"), env, 2000, this)
            terminalView.attachSession(terminalSession)
            terminalView.onScreenUpdated()
            
            runOnUiThread {
                terminalView.requestFocus()
                showKeyboard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Session startup failed", e)
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    // --- Реализация TerminalSessionClient с корректными Unit возвратами ---
    override fun onTextChanged(session: TerminalSession) { terminalView.onScreenUpdated() }
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) { finish() }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("termux", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.getItemAt(0)?.text?.let { terminalSession?.write(it.toString()) }
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0 
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun logError(tag: String, msg: String) { Log.e(tag, msg) }
    override fun logWarn(tag: String, msg: String) { Log.w(tag, msg) }
    override fun logInfo(tag: String, msg: String) { Log.i(tag, msg) }
    override fun logDebug(tag: String, msg: String) { Log.d(tag, msg) }
    override fun logVerbose(tag: String, msg: String) { Log.v(tag, msg) }
    override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e(tag, msg, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Stack", e) }

    // --- Реализация TerminalViewClient ---
    override fun onKeyDown(keyCode: Int, e: KeyEvent, s: TerminalSession): Boolean = false
    override fun onKeyUp(k: Int, e: KeyEvent): Boolean = false
    override fun onSingleTapUp(e: MotionEvent) { showKeyboard() }
    override fun onLongPress(e: MotionEvent): Boolean = false
    override fun onScale(s: Float): Float = s
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(c: Boolean) {}
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(cp: Int, ctrl: Boolean, s: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}

    override fun onDestroy() {
        terminalSession?.finishIfRunning()
        super.onDestroy()
    }
    }
    
