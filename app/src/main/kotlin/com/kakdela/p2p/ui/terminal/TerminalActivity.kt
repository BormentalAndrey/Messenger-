package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null

    private val TAG = "TerminalActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        terminalView = TerminalView(this, null).apply {
            setTextSize(16)
            keepScreenOn = true
        }

        setContentView(terminalView as View)

        setupTerminalSession()
    }

    private fun setupTerminalSession() {
        try {
            // -------------------- Directories --------------------
            val prefixDir = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")

            if (!prefixDir.exists()) prefixDir.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()

            // -------------------- Environment --------------------
            val env = arrayOf(
                "PATH=${prefixDir.absolutePath}/bin:/system/bin:/system/xbin",
                "HOME=${homeDir.absolutePath}",
                "PREFIX=${prefixDir.absolutePath}",
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8"
            )

            // -------------------- Shell --------------------
            val shellPath = when {
                File(prefixDir, "bin/bash").exists() ->
                    "${prefixDir.absolutePath}/bin/bash"
                File(prefixDir, "bin/sh").exists() ->
                    "${prefixDir.absolutePath}/bin/sh"
                else ->
                    "/system/bin/sh"
            }

            // -------------------- TerminalSession --------------------
            session = TerminalSession(
                shellPath,                    // executable
                homeDir.absolutePath,         // cwd
                arrayOf("-l"),                // args
                env,                          // environment
                object : TerminalSessionClient {

                    override fun onTextChanged(session: TerminalSession) {
                        terminalView.onScreenUpdated()
                    }

                    override fun onTitleChanged(session: TerminalSession) {
                        runOnUiThread {
                            title = session.title
                        }
                    }

                    override fun onSessionFinished(session: TerminalSession) {
                        Log.i(TAG, "Terminal session finished")
                        runOnUiThread {
                            if (!isFinishing) finish()
                        }
                    }

                    override fun onClipboardText(
                        session: TerminalSession,
                        text: String
                    ) {
                        // clipboard ignored intentionally
                    }

                    override fun onBell(session: TerminalSession) {
                        // bell ignored
                    }

                    override fun onColorsChanged(session: TerminalSession) {
                        terminalView.onScreenUpdated()
                    }

                    override fun onTerminalCursorStateChange(state: Boolean) {
                        // no-op
                    }
                },
                false // exitOnProcessExit (ВАЖНО: обязательный параметр!)
            )

            terminalView.attachSession(session)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start terminal session", e)
        }
    }

    override fun onResume() {
        super.onResume()
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        session?.finishIfRunning()
        super.onDestroy()
    }
}
