package com.example.moasis.audio

class AudioController(
    private val speechRecognizer: AndroidSpeechRecognizer,
    private val ttsEngine: AndroidTtsEngine,
    private val onVoiceEvent: (VoiceEvent) -> Unit,
    private val onSpeakingChanged: (Boolean) -> Unit,
) {
    private var isSpeaking: Boolean = false

    fun startListening() {
        speechRecognizer.startListening()
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun speak(text: String) {
        if (ttsEngine.speak(text)) {
            isSpeaking = true
            onSpeakingChanged(true)
        }
    }

    fun stopSpeaking(reason: String = "Speech stopped.") {
        if (isSpeaking) {
            ttsEngine.stop(reason)
        }
    }

    fun handleVoiceEvent(event: VoiceEvent) {
        if (event is VoiceEvent.PartialTranscript && isSpeaking) {
            stopSpeaking("Speech interrupted by user voice.")
        }
        onVoiceEvent(event)
    }

    fun onSpeechStarted() {
        isSpeaking = true
        onSpeakingChanged(true)
    }

    fun onSpeechCompleted() {
        isSpeaking = false
        onSpeakingChanged(false)
    }

    fun onSpeechStopped() {
        isSpeaking = false
        onSpeakingChanged(false)
    }

    fun destroy() {
        speechRecognizer.destroy()
        ttsEngine.shutdown()
    }
}
