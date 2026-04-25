package com.example.moasis.domain.nlu

fun interface SentenceEmbedder {
    fun embed(texts: List<String>): List<FloatArray>
}
