package com.example.moasis.presentation

import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.domain.model.UserTurn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class AppEvent {
    @Serializable
    @SerialName("voice_transcript")
    data class VoiceTranscript(val text: String, val isFinal: Boolean) : AppEvent()

    @Serializable
    @SerialName("user_submitted_turn")
    data class UserSubmittedTurn(val turn: UserTurn) : AppEvent()

    @Serializable
    @SerialName("user_tapped_action")
    data class UserTappedAction(val action: UiAction) : AppEvent()

    @Serializable
    @SerialName("tts_completed")
    data class TtsCompleted(val utteranceId: String) : AppEvent()

    @Serializable
    @SerialName("tts_interrupted")
    data class TtsInterrupted(val reason: String) : AppEvent()

    @Serializable
    @SerialName("llm_completed")
    data class LlmCompleted(val response: LlmResponse) : AppEvent()

    @Serializable
    @SerialName("llm_failed")
    data class LlmFailed(val error: String) : AppEvent()
}
