package com.kakdela.p2p.ai

import android.content.Context

object RagEngine {

    private val docs = mutableListOf<String>()

    fun addLocal(text: String) {
        docs += text
        if (docs.size > 50) docs.removeAt(0)
    }

    fun addWeb(text: String) {
        docs += text
        if (docs.size > 50) docs.removeAt(0)
    }

    fun relevant(query: String): String =
        docs.filter { it.contains(query, true) }
            .takeLast(5)
            .joinToString("\n")
}
