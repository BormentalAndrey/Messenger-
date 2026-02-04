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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

        private const val BOOTSTRAP_TAG = "bootstrap-2026.02.01-r1+apt.android-7"

        private const val BOOTSTRAP_AARCH64 = "bootstrap-aarch64.zip"
        private const val BOOTSTRAP_ARM = "bootstrap-arm.zip"
        private const val BOOTSTRAP_I686 = "bootstrap-i686.zip"
        private const val BOOTSTRAP_X86_64 = "bootstrap-x86_64.zip"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_termux)

        terminalView = findViewById(R.id.terminal_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(35)
        terminalView.keepScreenOn = true

        terminalView.setOnClickListener { showKeyboard() }

        settingsButton.setOnClickListener {
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
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })

        checkAndInstallBootstrap()
    }

    private fun showErrorAndExit(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(message)
                .setPositiveButton("Закрыть") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun checkAndInstallBootstrap() {
        val prefixDir = File(filesDir, "usr")
        val bashFile = File(prefixDir, "bin/bash")

        if (bashFile.exists() && bashFile.canExecute()) {
            setupSession()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Установка базовой системы Termux")
            .setMessage("Необходимо загрузить и установить базовые компоненты. Это займёт несколько минут. Продолжить?")
            .setPositiveButton("Да, загрузить") { _, _ ->
                val archiveName = detectBootstrapArchiveName()
                if (archiveName == null) {
                    showErrorAndExit("Не удалось определить архитектуру устройства")
                    return@setPositiveButton
                }

                val encodedTag = BOOTSTRAP_TAG.replace("+", "%2B")
                val downloadUrl =
                    "https://github.com/termux/termux-packages/releases/download/$encodedTag/$archiveName"

                startBootstrapDownload(downloadUrl, archiveName)
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun detectBootstrapArchiveName(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null

        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> BOOTSTRAP_AARCH64
            abi.contains("armeabi") -> BOOTSTRAP_ARM
            abi.contains("x86_64") -> BOOTSTRAP_X86_64
            abi.contains("x86") || abi.contains("i686") -> BOOTSTRAP_I686
            else -> null
        }
    }

    private fun startBootstrapDownload(urlStr: String, archiveName: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Установка Termux")
            setMessage("Подготовка...")
            setIndeterminate(true)
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
                val zipFile = File(tmpDir, archiveName)

                runOnUiThread {
                    progressDialog.setMessage("Загрузка базовой системы (~25–30 МБ)...")
                }
                downloadFile(urlStr, zipFile)

                runOnUiThread {
                    progressDialog.setMessage("Распаковка файлов...")
                }
                unzipBootstrap(zipFile)

                runOnUiThread {
                    progressDialog.setMessage("Создание символьных ссылок...")
                }
                applySymlinks()

                zipFile.delete()

                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Готово")
                        .setMessage("Базовая система установлена успешно.")
                        .setPositiveButton("Продолжить") { _, _ ->
                            setupSession()
                            showKeyboard()
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap installation failed", e)

                runOnUiThread {
                    progressDialog.dismiss()

                    val msg = when {
                        e.message?.contains("404") == true ->
                            "Файл не найден на сервере (404).\n\nВозможно, вышел новый релиз bootstrap.\nПроверьте https://github.com/termux/termux-packages/releases"
                        else -> e.localizedMessage ?: "Неизвестная ошибка"
                    }

                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Ошибка установки")
                        .setMessage(msg)
                        .setPositiveButton("Повторить") { _, _ ->
                            checkAndInstallBootstrap()
                        }
                        .setNegativeButton("Выход") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }.start()
    }

    private fun downloadFile(urlStr: String, destFile: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true

        val responseCode = conn.responseCode
        if (responseCode >= 400) {
            throw IOException("Сервер вернул ошибку $responseCode для URL: $urlStr")
        }

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }

    private fun unzipBootstrap(zipFile: File) {
        val rootDir = filesDir

        ZipInputStream(BufferedInputStream(zipFile.inputStream(), 8192)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry

            while (entry != null) {
                val outputFile = File(rootDir, entry.name)

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()

                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos, bufferSize = 8192)
                    }

                    if (
                        entry.name.startsWith("usr/bin/") ||
                        entry.name.startsWith("usr/libexec/") ||
                        entry.name.endsWith(".so") ||
                        entry.name.endsWith("/bash") ||
                        entry.name.endsWith("/sh")
                    ) {
                        outputFile.setExecutable(true, false)
                    }

                    outputFile.setReadable(true, false)
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinks() {
        val usrDir = File(filesDir, "usr")
        val symlinksFile = File(usrDir, "SYMLINKS.txt")

        if (!symlinksFile.exists()) return

        try {
            symlinksFile.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

                    val parts = trimmed.split("←")
                    if (parts.size == 2) {
                        val linkName = parts[0].trim()
                        val target = parts[1].trim()

                        val linkFile = File(usrDir, linkName)
                        linkFile.parentFile?.mkdirs()
                        linkFile.delete()

                        try {
                            Os.symlink(target, linkFile.absolutePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Не удалось создать symlink: $linkName → $target", e)
                        }
                    }
                }
            }

            symlinksFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки SYMLINKS.txt", e)
        }
    }

    private fun setupSession() {
        try {
            val usrDir = File(filesDir, "usr")
            val homeDir = File(filesDir, "home").apply { mkdirs() }

            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${homeDir.absolutePath}",
                "PREFIX=${usrDir.absolutePath}",
                "PATH=${usrDir.absolutePath}/bin:${usrDir.absolutePath}/local/bin",
                "LD_LIBRARY_PATH=${usrDir.absolutePath}/lib",
                "LANG=en_US.UTF-8",
                "ANDROID_DATA=/data",
                "ANDROID_ROOT=/system",
                "EXTERNAL_STORAGE=/sdcard"
            )

            val shellPath = File(usrDir, "bin/bash").absolutePath
            if (!File(shellPath).canExecute()) {
                throw IOException("bash не найден или не исполняемый: $shellPath")
            }

            terminalSession = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("--login"),
                env,
                200,
                this
            )

            terminalView.attachSession(terminalSession)
            terminalView.onScreenUpdated()

        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить сессию терминала", e)

            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Ошибка запуска терминала")
                    .setMessage(e.localizedMessage ?: "Неизвестная ошибка")
                    .setPositiveButton("Закрыть") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isAcceptingText) {
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        } else {
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_FORCED)
        }
    }

    // -------------------------------------------------------------------------
    // TerminalSessionClient
    // -------------------------------------------------------------------------

    override fun onTextChanged(session: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {
    }

    override fun onSessionFinished(session: TerminalSession) {
        finish()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { text ->
            terminalSession?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {
    }

    // -------------------------------------------------------------------------
    // TerminalViewClient
    // -------------------------------------------------------------------------

    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }

    override fun onLongPress(e: MotionEvent): Boolean = false

    override fun onScale(scale: Float): Float = scale

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onCodePoint(cp: Int, ctrl: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
    }

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun getTerminalCursorStyle(): Int = 0

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
    }

    override fun onColorsChanged(session: TerminalSession) {
    }

    // -------------------------------------------------------------------------
    // Логирование (ВАЖНО: должны возвращать Unit)
    // -------------------------------------------------------------------------

    override fun logError(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    override fun logWarn(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun logInfo(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun logDebug(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun logVerbose(tag: String, msg: String) {
        Log.v(tag, msg)
    }

    override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) {
        Log.e(tag, msg, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, "Stack trace", e)
    }

    override fun onDestroy() {
        terminalSession?.finishIfRunning()
        super.onDestroy()
    }
    }
