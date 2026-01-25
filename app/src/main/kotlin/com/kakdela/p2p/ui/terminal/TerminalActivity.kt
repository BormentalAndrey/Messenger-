package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalView
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null
    private val TAG = "TerminalActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // -------------------- TerminalView --------------------
        terminalView = TerminalView(this).apply {
            setTextSize(14)
        }
        setContentView(terminalView)

        // -------------------- Termux Shell --------------------
        try {
            // Инициализация Termux Shell Environment
            val shellEnv = TermuxShellEnvironment()

            // Определяем путь к Termux PREFIX (где будут установлены пакеты)
            // Обычно Termux использует: /data/data/com.termux/files/usr
            val termuxPrefix = File(filesDir, "usr")

            if (!termuxPrefix.exists()) {
                termuxPrefix.mkdirs()
            }

            // Определяем исполняемый файл оболочки
            val shellExecutable = File(termuxPrefix, "bin/bash")
            if (!shellExecutable.exists()) {
                Log.w(TAG, "Bash shell not found in Termux PREFIX, fallback to /system/bin/sh")
            }

            // Создаем менеджер shell
            val shellManager = TermuxShellManager()

            // Запускаем shell
            session = shellManager.startShell(
                shellEnv,
                workingDirectory = termuxPrefix.absolutePath,
                executable = if (shellExecutable.exists()) shellExecutable.absolutePath else "/system/bin/sh"
            )

            // Присоединяем терминальную сессию к TerminalView
            session?.let { terminalView.attachSession(it) }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Termux shell", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            session?.finish() // закрываем shell сессию
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish shell session", e)
        }
    }
}
