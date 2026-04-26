package com.example.moasis.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class AndroidSpeechRecognizer(
    context: Context,
    private val onVoiceEvent: (VoiceEvent) -> Unit,
) {
    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer? = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
        SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onVoiceEvent(VoiceEvent.ListeningStarted)
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onVoiceEvent(VoiceEvent.ListeningStopped)
                }

                override fun onError(error: Int) {
                    onVoiceEvent(VoiceEvent.ListeningStopped)
                    onVoiceEvent(VoiceEvent.Error(messageFor(error)))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val transcript = matches.firstOrNull()?.trim().orEmpty()
                    if (transcript.isNotBlank()) {
                        onVoiceEvent(VoiceEvent.FinalTranscript(transcript))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val transcript = matches.firstOrNull()?.trim().orEmpty()
                    if (transcript.isNotBlank()) {
                        onVoiceEvent(VoiceEvent.PartialTranscript(transcript))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    } else {
        null
    }

    fun startListening() {
        val instance = recognizer
        if (instance == null) {
            onVoiceEvent(VoiceEvent.Error("Speech recognition is unavailable on this device."))
            return
        }

        instance.cancel()
        instance.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, ENGLISH_LANGUAGE_TAG)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, ENGLISH_LANGUAGE_TAG)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
        )
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
    }

    private fun messageFor(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio failed."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition stopped."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech recognition is unavailable right now."
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition service failed."
            else -> "Speech recognition failed."
        }
    }

    companion object {
        private val ENGLISH_LANGUAGE_TAG = Locale.US.toLanguageTag()
    }
}
