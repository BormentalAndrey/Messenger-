package com.kakdela.p2p.ui.terminal

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем Termux TerminalView
        terminalView = TerminalView(this).apply {
            setTextSize(14)
        }
        setContentView(terminalView)

        // Инициализация Termux Shell Environment
        val shellEnv = TermuxShellEnvironment()

        // Определяем путь к Termux PREFIX (где установлены пакеты)
        // Обычно Termux использует: /data/data/com.termux/files/usr
        val termuxPrefix = File(applicationContext.filesDir, "usr") // <-- твой локальный PREFIX

        if (!termuxPrefix.exists()) {
            termuxPrefix.mkdirs() // создаем, если нет
        }

        // Полноценная Termux оболочка
        val shellManager = TermuxShellManager()
        session = shellManager.startShell(
            shellEnv,
            workingDirectory = termuxPrefix.absolutePath,
            executable = File(termuxPrefix, "bin/bash").absolutePath
        )

        // Присоединяем терминальную сессию к TerminalView
        session?.let { terminalView.attachSession(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            session?.finish() // закрываем shell
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
