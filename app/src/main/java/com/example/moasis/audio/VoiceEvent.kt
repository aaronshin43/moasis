package com.example.moasis.audio

sealed class VoiceEvent {
    data object ListeningStarted : VoiceEvent()

    data object ListeningStopped : VoiceEvent()

    data class PartialTranscript(val text: String) : VoiceEvent()

    data class FinalTranscript(val text: String) : VoiceEvent()

    data class Error(val message: String) : VoiceEvent()
}
