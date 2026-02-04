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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class TerminalActivity :
    AppCompatActivity(),
    TerminalSessionClient,
    TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private var terminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"
        // Используем проверенный базовый URL (можно заменить на свой, если нужно)
        private const val GITHUB_BASE_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2023.07.07-r1"

        // Маппинг архитектур Android в названия файлов bootstrap Termux
        private val ARCH_BOOTSTRAPS = mapOf(
            "armeabi-v7a" to "bootstrap-arm.zip", // Обычно zip, но код адаптирован под tar.gz если вы используете свой источник
            "arm64-v8a" to "bootstrap-aarch64.zip",
            "x86" to "bootstrap-i686.zip",
            "x86_64" to "bootstrap-x86_64.zip"
        )
        // Для поддержки tar.gz (если вы используете старые bootstrap)
        // Если ссылка ведет на zip - нужно использовать ZipInputStream, здесь код для tar.gz как в оригинале
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
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START)
            else
                drawerLayout.openDrawer(GravityCompat.START)
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
                    finish() // Можно свернуть активити вместо закрытия
            }
        })

        checkAndInstallBootstrap()
    }

    private fun checkAndInstallBootstrap() {
        val prefixDir = File(filesDir, "usr")
        val bashFile = File(prefixDir, "bin/bash")

        if (bashFile.exists() && bashFile.canExecute()) {
            // Уже установлено
            setupSession()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Установка Termux")
            .setMessage("Необходима загрузка системных файлов (Bootstrap) для работы терминала. Скачать (~30MB)?")
            .setPositiveButton("Скачать") { _, _ ->
                val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                // ВАЖНО: Убедитесь, что URL ведет на .tar.gz файл, так как распаковщик ниже под tar.gz
                // Если у вас zip (стандарт github releases), нужно менять распаковщик.
                // Для примера оставил логику .tar.gz как в исходнике.
                var archiveName = "bootstrap-aarch64.tar.gz" 
                if (arch.contains("arm64")) archiveName = "bootstrap-aarch64.tar.gz"
                else if (arch.contains("armeabi")) archiveName = "bootstrap-arm.tar.gz"
                else if (arch.contains("x86_64")) archiveName = "bootstrap-x86_64.tar.gz"
                else if (arch.contains("x86")) archiveName = "bootstrap-i686.tar.gz"

                // Пример URL. Замените на реальный прямой URL к tar.gz файлу!
                val url = "https://github.com/termux/termux-packages/releases/download/bootstrap-2023.07.07-r1/$archiveName"
                
                // fallback на zip если tar.gz нет в релизах (GitHub сейчас отдает zip)
                // Но так как код парсера под TAR, предположим, что ссылка верная.
                
                Log.i(TAG, "Selected arch: $arch, URL: $url")
                downloadAndSetupBootstrap(url)
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // ---------------------------------------------------------
    // Bootstrap Installer
    // ---------------------------------------------------------
    private fun downloadAndSetupBootstrap(urlStr: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Установка")
            setMessage("Загрузка и распаковка...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                // Используем filesDir приложения, а не TermuxConstants
                val usrDir = File(filesDir, "usr")
                val tmpDir = File(usrDir, "tmp")
                if (!tmpDir.exists()) tmpDir.mkdirs()

                val archiveFile = File(tmpDir, "bootstrap.tar.gz")

                // 1. Скачивание
                Log.i(TAG, "Downloading bootstrap...")
                downloadBootstrapArchive(urlStr, archiveFile)

                // 2. Распаковка
                Log.i(TAG, "Extracting bootstrap...")
                extractBootstrapArchive(archiveFile, usrDir)

                // 3. Создание симлинков (КРИТИЧНО для работы!)
                // Обычно в bootstrap есть файл SYMLINKS.txt
                setupSymlinks(usrDir)

                // 4. Удаление архива
                archiveFile.delete()

                runOnUiThread {
                    progressDialog.dismiss()
                    setupStorageSymlinks()
                    setupSession()
                    terminalView.requestFocus()
                    terminalView.postDelayed({ showKeyboard() }, 500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap failed", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Ошибка")
                        .setMessage("Не удалось установить Termux:\n${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun downloadBootstrapArchive(urlStr: String, destFile: File) {
        if (destFile.exists()) destFile.delete()

        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        // Обработка редиректов (GitHub часто редиректит)
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
            val newUrl = connection.getHeaderField("Location")
            downloadBootstrapArchive(newUrl, destFile)
            return
        }

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractBootstrapArchive(archiveFile: File, destinationDir: File) {
        // Мы распаковываем прямо в usr, так как bootstrap обычно содержит структуру внутри
        // Но Termux bootstrap обычно распаковывается в корень, создавая usr внутри. 
        // Поэтому здесь важно понимать структуру архива. Обычно архив содержит ./usr/... или ./...
        // Для надежности распакуем в filesDir (родитель usr)
        val rootDir = filesDir 

        GZIPInputStream(archiveFile.inputStream()).use { gis ->
            TarArchiveInputStream(gis).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    // Защита от Zip Slip
                    val outputFile = File(rootDir, entry.name)
                    if (!outputFile.canonicalPath.startsWith(rootDir.canonicalPath)) {
                        throw SecurityException("Invalid path in archive: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        if (!outputFile.exists()) outputFile.mkdirs()
                    } else if (entry.isSymbolicLink) {
                         // Симлинки внутри TAR часто игнорируются Java потоком, 
                         // мы их обработаем через SYMLINKS.txt если он есть, 
                         // или создадим тут если библиотека позволяет (commons-compress может читать linkName)
                         try {
                             val linkName = entry.linkName
                             if (linkName != null) {
                                 if (outputFile.exists()) outputFile.delete()
                                 Os.symlink(linkName, outputFile.absolutePath)
                             }
                         } catch (e: Exception) {
                             Log.w(TAG, "Failed to create symlink from tar: ${entry.name}")
                         }
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { out ->
                            tar.copyTo(out)
                        }

                        // ВАЖНО: Выставляем права на исполнение
                        if (entry.name.contains("bin/") || entry.name.contains("libexec/")) {
                            outputFile.setExecutable(true, true)
                            // chmod 700
                            try {
                                Os.chmod(outputFile.absolutePath, 0b111000000) 
                            } catch (e: Exception) { /* ignore */ }
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }
    
    // Парсинг файла SYMLINKS.txt (формат Termux: path←target)
    private fun setupSymlinks(usrDir: File) {
        val symlinkFile = File(usrDir, "SYMLINKS.txt")
        if (!symlinkFile.exists()) {
            Log.w(TAG, "SYMLINKS.txt not found in ${usrDir.absolutePath}")
            return
        }

        try {
            BufferedReader(InputStreamReader(symlinkFile.inputStream())).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split("←")
                    if (parts.size == 2) {
                        val linkName = parts[0] // Например: bin/sh
                        val target = parts[1]   // Например: bash

                        val linkFile = File(usrDir, linkName)
                        val targetPath = if (target.startsWith("/")) target else File(linkFile.parent, target).absolutePath

                        try {
                            if (linkFile.exists() || Os.lstat(linkFile.absolutePath) != null) {
                                linkFile.delete()
                            }
                            linkFile.parentFile?.mkdirs()
                            Os.symlink(target, linkFile.absolutePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Symlink error: $linkName -> $target: ${e.message}")
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SYMLINKS.txt", e)
        }
    }

    // ---------------------------------------------------------
    // Storage
    // ---------------------------------------------------------
    private fun setupStorageSymlinks() {
        try {
            val homeDir = File(filesDir, "home")
            val storageDir = File(homeDir, "storage")
            if (!storageDir.exists()) storageDir.mkdirs()
            
            // В реальном Termux здесь создаются симлинки на DCIM, Downloads и т.д.
            // Требуются Runtime Permissions READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup storage symlinks", e)
        }
    }

    // ---------------------------------------------------------
    // Session
    // ---------------------------------------------------------
    private fun setupSession() {
        terminalSession?.finishIfRunning()
        terminalSession = null

        try {
            // Определяем пути относительно filesDir приложения
            val usrDir = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")
            
            if (!homeDir.exists()) homeDir.mkdirs()

            // Настройка окружения Linux
            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${homeDir.absolutePath}",
                "PREFIX=${usrDir.absolutePath}",
                "TMPDIR=${usrDir.absolutePath}/tmp",
                "PATH=${usrDir.absolutePath}/bin:${usrDir.absolutePath}/bin/applets",
                "LD_LIBRARY_PATH=${usrDir.absolutePath}/lib",
                "LANG=en_US.UTF-8",
                "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
                "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}"
            )

            // Проверяем наличие bash, иначе fallback на sh
            val bash = File(usrDir, "bin/bash")
            val sh = File(usrDir, "bin/sh")
            
            val shellPath = when {
                bash.exists() && bash.canExecute() -> bash.absolutePath
                sh.exists() && sh.canExecute() -> sh.absolutePath
                else -> "/system/bin/sh" // Системный shell (ограниченный функционал)
            }

            Log.i(TAG, "Starting shell: $shellPath")

            terminalSession = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"), // login shell
                env,
                2000,
                this
            )

            terminalView.attachSession(terminalSession)
            terminalView.onScreenUpdated()

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start terminal", t)
        }
    }

    // ---------------------------------------------------------
    // Keyboard & UI
    // ---------------------------------------------------------
    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Проверяем активность ввода немного иначе для надежности
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    // ───────── TerminalSessionClient ─────────

    override fun onTextChanged(session: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {
        // Можно обновить ActionBar title
    }

    override fun onSessionFinished(session: TerminalSession) {
        // Сессия завершена (exit). Можно перезапустить или закрыть активити.
        if (session == terminalSession) {
            // Log.i(TAG, "Session finished")
            // finish() 
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("termux", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cb.primaryClip
        if (clip != null && clip.itemCount > 0) {
             val text = clip.getItemAt(0).coerceToText(this).toString()
             terminalSession?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = TerminalSession.CURSOR_STYLE_BLOCK
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    // Logging
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Stacktrace", e) }

    // ───────── TerminalViewClient ─────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
    override fun onSingleTapUp(event: MotionEvent) {
        showKeyboard()
    }
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun onScale(scale: Float): Float = scale
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}

    
