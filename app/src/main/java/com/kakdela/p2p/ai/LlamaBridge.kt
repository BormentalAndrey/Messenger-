package com.kakdela.p2p.ai

object LlamaBridge {

    init {
        System.loadLibrary("llama")
    }

    external fun init(modelPath: String)
    external fun prompt(text: String): String
}
