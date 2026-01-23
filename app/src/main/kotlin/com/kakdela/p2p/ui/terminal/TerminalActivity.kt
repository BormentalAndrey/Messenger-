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
import android.widget.LinearLayout
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

    private val commandHistory = ArrayList<String>()
    private var historyIndex = -1

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

        // Обработка истории команд (стрелки вверх/вниз)
        commandEditText.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        navigateHistory(true)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        navigateHistory(false)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Приветствие
        appendOutput("Welcome to Simple Terminal\nCurrent dir: ${currentWorkingDir.absolutePath}\n")
    }

    private fun createLayout(): View {
        // Создаем корневой layout программно для легкой интеграции
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        // ScrollView с выводом
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            id = View.generateViewId()
        }

        outputTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            id = View.generateViewId()
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(8, 8, 8, 8)
        }

        scrollView.addView(outputTextView)

        // Нижняя панель с вводом и кнопками
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 0)
        }

        commandEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            hint = "Enter command (e.g., ls, cd ..)"
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            id = View.generateViewId()
        }

        executeButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Execute"
            id = View.generateViewId()
        }

        clearButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Clear"
            id = View.generateViewId()
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

        // Добавляем в историю
        commandHistory.add(0, command)
        historyIndex = -1

        executor.execute {
            try {
                val parts = command.split("\\s+".toRegex()).toMutableList()
                val cmd = parts[0]
                val args = if (parts.size > 1) parts.subList(1, parts.size).toTypedArray() else emptyArray()

                when (cmd) {
                    "cd" -> handleCd(args)
                    "pwd" -> handlePwd()
                    "ls" -> handleLs(args)
                    "mkdir" -> handleMkdir(args)
                    "rmdir" -> handleRmdir(args)
                    "touch" -> handleTouch(args)
                    "rm" -> handleRm(args)
                    "cat" -> handleCat(args)
                    "echo" -> handleEcho(args)
                    "help" -> handleHelp()
                    else -> handleShellCommand(command)
                }
            } catch (e: Exception) {
                handler.post { appendOutput("Error: ${e.message}\n") }
            }
        }
    }

    private fun handleCd(args: Array<String>) {
        if (args.isEmpty()) {
            currentWorkingDir = File(System.getProperty("user.home") ?: Environment.getExternalStorageDirectory().absolutePath)
        } else {
            var targetPath = args[0]
            if (targetPath == "~") {
                targetPath = System.getProperty("user.home") ?: Environment.getExternalStorageDirectory().absolutePath
            }
            val targetDir = if (targetPath.startsWith("/")) File(targetPath) else File(currentWorkingDir, targetPath)
            if (targetDir.exists() && targetDir.isDirectory) {
                currentWorkingDir = targetDir.canonicalFile
            } else {
                handler.post { appendOutput("Directory not found: ${args[0]}\n") }
                return
            }
        }
        handler.post { appendOutput("Current dir: ${currentWorkingDir.absolutePath}\n") }
    }

    private fun handlePwd() {
        handler.post { appendOutput("${currentWorkingDir.absolutePath}\n") }
    }

    private fun handleLs(args: Array<String>) {
        val dir = if (args.isNotEmpty()) File(currentWorkingDir, args[0]) else currentWorkingDir
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()?.map { it.name } ?: emptyList()
            val output = files.joinToString("\n")
            handler.post { appendOutput("$output\n") }
        } else {
            handler.post { appendOutput("Directory not found: ${args.getOrNull(0) ?: ""}\n") }
        }
    }

    private fun handleMkdir(args: Array<String>) {
        if (args.isEmpty()) {
            handler.post { appendOutput("Usage: mkdir <directory>\n") }
            return
        }
        val dir = File(currentWorkingDir, args[0])
        if (dir.mkdirs()) {
            handler.post { appendOutput("Directory created: ${dir.absolutePath}\n") }
        } else {
            handler.post { appendOutput("Failed to create directory: ${args[0]}\n") }
        }
    }

    private fun handleRmdir(args: Array<String>) {
        if (args.isEmpty()) {
            handler.post { appendOutput("Usage: rmdir <directory>\n") }
            return
        }
        val dir = File(currentWorkingDir, args[0])
        if (dir.exists() && dir.isDirectory && dir.delete()) {
            handler.post { appendOutput("Directory removed: ${dir.absolutePath}\n") }
        } else {
            handler.post { appendOutput("Failed to remove directory: ${args[0]}\n") }
        }
    }

    private fun handleTouch(args: Array<String>) {
        if (args.isEmpty()) {
            handler.post { appendOutput("Usage: touch <file>\n") }
            return
        }
        val file = File(currentWorkingDir, args[0])
        if (file.createNewFile()) {
            handler.post { appendOutput("File created: ${file.absolutePath}\n") }
        } else {
            handler.post { appendOutput("Failed to create file: ${args[0]}\n") }
        }
    }

    private fun handleRm(args: Array<String>) {
        if (args.isEmpty()) {
            handler.post { appendOutput("Usage: rm <file>\n") }
            return
        }
        val file = File(currentWorkingDir, args[0])
        if (file.exists() && file.delete()) {
            handler.post { appendOutput("File removed: ${file.absolutePath}\n") }
        } else {
            handler.post { appendOutput("Failed to remove file: ${args[0]}\n") }
        }
    }

    private fun handleCat(args: Array<String>) {
        if (args.isEmpty()) {
            handler.post { appendOutput("Usage: cat <file>\n") }
            return
        }
        val file = File(currentWorkingDir, args[0])
        if (file.exists() && file.isFile) {
            try {
                val content = file.readText()
                handler.post { appendOutput("$content\n") }
            } catch (e: Exception) {
                handler.post { appendOutput("Error reading file: ${e.message}\n") }
            }
        } else {
            handler.post { appendOutput("File not found: ${args[0]}\n") }
        }
    }

    private fun handleEcho(args: Array<String>) {
        val message = args.joinToString(" ")
        handler.post { appendOutput("$message\n") }
    }

    private fun handleHelp() {
        val helpText = """
            Available commands:
            - cd [dir]: Change directory
            - pwd: Print working directory
            - ls [dir]: List files
            - mkdir <dir>: Create directory
            - rmdir <dir>: Remove empty directory
            - touch <file>: Create file
            - rm <file>: Remove file
            - cat <file>: Display file content
            - echo [text]: Print text
            - help: Show this help
            - clear: Clear screen (via button)
            Other commands: Attempt to run via shell
        """.trimIndent()
        handler.post { appendOutput("$helpText\n") }
    }

    private fun handleShellCommand(fullCommand: String) {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", fullCommand))
        val writer = OutputStreamWriter(process.outputStream)
        writer.close()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        val output = StringBuilder()
        while (reader.readLine().also { line = it } != null) {
            output.append("$line\n")
        }
        reader.close()

        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        while (errorReader.readLine().also { line = it } != null) {
            output.append("Error: $line\n")
        }
        errorReader.close()

        process.waitFor()
        handler.post { appendOutput(output.toString()) }
    }

    private fun navigateHistory(up: Boolean) {
        if (commandHistory.isEmpty()) return

        if (up) {
            if (historyIndex < commandHistory.size - 1) {
                historyIndex++
                commandEditText.setText(commandHistory[historyIndex])
            }
        } else {
            if (historyIndex > 0) {
                historyIndex--
                commandEditText.setText(commandHistory[historyIndex])
            } else if (historyIndex == 0) {
                historyIndex = -1
                commandEditText.text.clear()
            }
        }
        commandEditText.setSelection(commandEditText.text.length)
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
