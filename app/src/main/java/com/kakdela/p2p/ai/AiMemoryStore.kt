package com.kakdela.p2p.ai

object AiMemoryStore {

    private val memory = mutableListOf<String>()

    fun remember(text: String) {
        if (text.length > 20) memory += text
        if (memory.size > 50) memory.removeAt(0)
    }

    fun context(): String =
        memory.joinToString("\n")
}
