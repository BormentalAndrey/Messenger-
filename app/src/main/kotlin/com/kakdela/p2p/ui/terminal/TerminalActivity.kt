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
import java.io.*
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
        // ВАЖНО: Используем точку перед android-7 (как в GitHub)
        private const val BOOTSTRAP_TAG = "bootstrap-2024.12.18-r1+apt.android-7"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_termux)

        // Инициализация View из вашего XML
        terminalView = findViewById(R.id.terminal_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(35)
        terminalView.keepScreenOn = true
        
        // Открытие клавиатуры при клике на терминал
        terminalView.setOnClickListener { showKeyboard() }

        settingsButton.setOnClickListener {
            // Можно добавить открытие активити настроек здесь
            Log.d(TAG, "Settings button clicked")
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
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START)
                else
                    finish()
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
            .setTitle("Установка Termux")
            .setMessage("Необходимо загрузить базовые компоненты системы. Продолжить?")
            .setPositiveButton("Загрузить") { _, _ ->
                val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val archiveName = when {
                    arch.contains("arm64") || arch.contains("aarch64") -> "bootstrap-aarch64.zip"
                    arch.contains("armeabi") || arch.contains("arm") -> "bootstrap-arm.zip"
                    arch.contains("x86_64") -> "bootstrap-x86_64.zip"
                    arch.contains("x86") || arch.contains("i686") -> "bootstrap-i686.zip"
                    else -> "bootstrap-aarch64.zip"
                }

                // Заменяем '+' на '%2B' для корректного URL GitHub
                val encodedTag = BOOTSTRAP_TAG.replace("+", "%2B")
                val downloadUrl = "https://github.com/termux/termux-packages/releases/download/$encodedTag/$archiveName"
                
                startBootstrapDownload(downloadUrl)
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startBootstrapDownload(urlStr: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Загрузка")
            setMessage("Подготовка...")
            setIndeterminate(true)
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }
                val zipFile = File(tmpDir, "bootstrap.zip")

                // 1. Скачивание
                runOnUiThread { progressDialog.setMessage("Скачивание файлов...") }
                downloadFile(urlStr, zipFile)

                // 2. Распаковка
                runOnUiThread { progressDialog.setMessage("Распаковка (это займет время)...") }
                unzipBootstrap(zipFile)

                // 3. Создание симлинков (Критично для Linux команд)
                runOnUiThread { progressDialog.setMessage("Настройка системы...") }
                applySymlinks()

                zipFile.delete()

                runOnUiThread {
                    progressDialog.dismiss()
                    setupSession()
                    showKeyboard()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Install Error", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Ошибка загрузки")
                        .setMessage(e.localizedMessage ?: "Неизвестная ошибка")
                        .setPositiveButton("Повторить") { _, _ -> checkAndInstallBootstrap() }
                        .setNegativeButton("Выход") { _, _ -> finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun downloadFile(urlStr: String, destFile: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true

        if (conn.responseCode >= 400) {
            throw IOException("Сервер вернул ошибку ${conn.responseCode} для URL: $urlStr")
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

                    // Установка прав доступа на исполнение
                    if (entry.name.contains("bin/") || entry.name.endsWith(".so") || entry.name.contains("libexec/")) {
                        outputFile.setExecutable(true, false)
                        outputFile.setReadable(true, false)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinks() {
        val usrDir = File(filesDir, "usr")
        val symlinksTxt = File(usrDir, "SYMLINKS.txt")
        if (!symlinksTxt.exists()) return

        try {
            symlinksTxt.readLines().forEach { line ->
                val parts = line.split("←")
                if (parts.size == 2) {
                    val linkPath = File(usrDir, parts[0])
                    val target = parts[1]
                    linkPath.delete()
                    try {
                        Os.symlink(target, linkPath.absolutePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Symlink error: ${parts[0]} -> $target", e)
                    }
                }
            }
            symlinksTxt.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Symlinks processing error", e)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start terminal session", e)
        }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    // --- Реализация интерфейсов ---

    override fun onTextChanged(session: TerminalSession) = terminalView.onScreenUpdated()
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) = finish()
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("termux", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cb.primaryClip?.getItemAt(0)?.text
        if (clip != null) terminalSession?.write(clip.toString())
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0 
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    // Логирование (возвращает Unit)
    override fun logError(tag: String, msg: String) { Log.e(tag, msg) }
    override fun logWarn(tag: String, msg: String) { Log.w(tag, msg) }
    override fun logInfo(tag: String, msg: String) { Log.i(tag, msg) }
    override fun logDebug(tag: String, msg: String) { Log.d(tag, msg) }
    override fun logVerbose(tag: String, msg: String) { Log.v(tag, msg) }
    override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e(tag, msg, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Stack", e) }

    // Касания и жесты
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
    
