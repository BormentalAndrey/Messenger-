package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputTextView: TextView
    private lateinit var commandEditText: EditText
    private lateinit var executeButton: Button
    private lateinit var clearButton: Button
    private lateinit var scrollView: ScrollView

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var currentWorkingDir: File = Environment.getExternalStorageDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())  // Динамический layout для интеграции

        // Инициализация UI
        outputTextView = findViewById(R.id.outputTextView)
        commandEditText = findViewById(R.id.commandEditText)
        executeButton = findViewById(R.id.executeButton)
        clearButton = findViewById(R.id.clearButton)
        scrollView = findViewById(R.id.scrollView)

        outputTextView.movementMethod = ScrollingMovementMethod()

        // Обработчики
        executeButton.setOnClickListener { executeCommand() }
        clearButton.setOnClickListener { outputTextView.text = "" }

        commandEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                executeCommand()
                true
            } else false
        }

        // Приветствие
        appendOutput("Welcome to Simple Terminal\nCurrent dir: ${currentWorkingDir.absolutePath}\n")
    }

    private fun createLayout(): View {
        // Создаем корневой layout программно для легкой интеграции
        val rootLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        // ScrollView с выводом
        scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            id = android.view.View.generateViewId()
        }

        outputTextView = android.widget.TextView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            id = android.view.View.generateViewId()
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(8, 8, 8, 8)
        }

        scrollView.addView(outputTextView)

        // Нижняя панель с вводом и кнопками
        val bottomBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 0)
        }

        commandEditText = android.widget.EditText(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            hint = "Enter command (e.g., ls, cd ..)"
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            id = android.view.View.generateViewId()
        }

        executeButton = android.widget.Button(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Execute"
            id = android.view.View.generateViewId()
        }

        clearButton = android.widget.Button(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Clear"
            id = android.view.View.generateViewId()
        }

        bottomBar.addView(commandEditText)
        bottomBar.addView(executeButton)
        bottomBar.addView(clearButton)

        rootLayout.addView(scrollView)
        rootLayout.addView(bottomBar)

        return rootLayout
    }

    private fun executeCommand() {
        val command = commandEditText.text.toString().trim()
        if (command.isEmpty()) return

        appendOutput("> $command\n")
        commandEditText.text.clear()

        executor.execute {
            try {
                val parts = command.split(" ").toMutableList()
                val cmd = parts[0]
                val args = parts.subList(1, parts.size).toTypedArray()

                if (cmd == "cd") {
                    handleCd(args)
                } else {
                    val process = ProcessBuilder()
                        .command(cmd, *args)
                        .directory(currentWorkingDir)
                        .redirectErrorStream(true)
                        .start()

                    val writer = OutputStreamWriter(process.outputStream)
                    writer.close()

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    val output = StringBuilder()
                    while (reader.readLine().also { line = it } != null) {
                        output.append("$line\n")
                    }
                    reader.close()

                    process.waitFor()
                    handler.post { appendOutput(output.toString()) }
                }
            } catch (e: Exception) {
                handler.post { appendOutput("Error: ${e.message}\n") }
            }
        }
    }

    private fun handleCd(args: Array<String>) {
        if (args.isEmpty()) {
            currentWorkingDir = Environment.getExternalStorageDirectory()
        } else {
            val targetDir = File(currentWorkingDir, args[0])
            if (targetDir.exists() && targetDir.isDirectory) {
                currentWorkingDir = targetDir
            } else {
                handler.post { appendOutput("Directory not found: ${args[0]}\n") }
                return
            }
        }
        handler.post { appendOutput("Current dir: ${currentWorkingDir.absolutePath}\n") }
    }

    private fun appendOutput(text: String) {
        outputTextView.append(text)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
