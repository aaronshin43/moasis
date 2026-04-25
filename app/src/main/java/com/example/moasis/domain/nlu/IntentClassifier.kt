package com.example.moasis.domain.nlu

fun interface IntentClassifier {
    fun match(text: String): IntentMatch
}
