package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.Log
import com.termux.terminal.TerminalEmulator
import java.io.File

class TerminalActivity : AppCompatActivity(),
    TerminalViewClient,
    TerminalSessionClient {

    private lateinit var terminalView: TerminalView
    private lateinit var terminalSession: TerminalSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Terminal View
        terminalView = TerminalView(this, null)
        terminalView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        terminalView.setTerminalViewClient(this)
        root.addView(terminalView)
        setContentView(root)

        // === TERMUX ENV INIT ===
        val homeDir = File(filesDir, "home").apply { mkdirs() }
        val binDir = File(filesDir, "usr/bin").apply { mkdirs() }

        // shell
        val shellPath = "/system/bin/sh"

        val env = arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PWD=${homeDir.absolutePath}",
            "TMPDIR=${cacheDir.absolutePath}",
            "PATH=/system/bin:/system/xbin:${binDir.absolutePath}",
            "TERM=xterm-256color",
            "LANG=C.UTF-8"
        )

        // args
        val args = arrayOf("-l")

        terminalSession = TerminalSession(
            shellPath,
            homeDir.absolutePath,
            args,
            env,
            1000, // transcript rows
            this
        )

        terminalView.attachSession(terminalSession)
    }

    override fun onResume() {
        super.onResume()
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        if (::terminalSession.isInitialized) {
            terminalSession.finishIfRunning()
        }
        super.onDestroy()
    }

    // =========================
    // TerminalViewClient
    // =========================

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {}

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        session.writeCodePoint(ctrlDown, codePoint)
        return true
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String, message: String) = Log.e(tag, message)
    override fun logWarn(tag: String, message: String) = Log.w(tag, message)
    override fun logInfo(tag: String, message: String) = Log.i(tag, message)
    override fun logDebug(tag: String, message: String) = Log.d(tag, message)
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message)
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Log.e(tag, message, e)
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, "stacktrace", e)

    // =========================
    // TerminalSessionClient
    // =========================

    override fun onTextChanged(session: TerminalSession) {
        terminalView.invalidate()
    }

    override fun onTitleChanged(session: TerminalSession) {}

    override fun onSessionFinished(session: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

    override fun onPasteTextFromClipboard(session: TerminalSession) {}

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    }
