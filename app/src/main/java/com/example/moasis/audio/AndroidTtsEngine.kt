package com.example.moasis.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidTtsEngine(
    context: Context,
    private val onStarted: () -> Unit,
    private val onCompleted: (utteranceId: String) -> Unit,
    private val onStopped: (reason: String) -> Unit,
) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var lastStopReason: String? = null
    private var activeUtteranceId: String? = null

    init {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                onStarted()
            }

            override fun onDone(utteranceId: String) {
                activeUtteranceId = null
                onCompleted(utteranceId)
            }

            override fun onError(utteranceId: String) {
                activeUtteranceId = null
                onStopped("Text to speech failed.")
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                activeUtteranceId = null
                onStopped(lastStopReason ?: if (interrupted) "Speech interrupted." else "Speech stopped.")
                lastStopReason = null
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            textToSpeech.language = Locale.US
        } else {
            onStopped("Text to speech is unavailable.")
        }
    }

    fun speak(text: String): Boolean {
        if (!isReady || text.isBlank()) {
            return false
        }

        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        lastStopReason = null
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return true
    }

    fun stop(reason: String = "Speech stopped.") {
        lastStopReason = reason
        textToSpeech.stop()
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
