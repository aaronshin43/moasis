package com.example.moasis.presentation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UiAction {
    @Serializable
    @SerialName("next")
    data object Next : UiAction()

    @Serializable
    @SerialName("repeat")
    data object Repeat : UiAction()

    @Serializable
    @SerialName("back")
    data object Back : UiAction()

    @Serializable
    @SerialName("call_emergency")
    data object CallEmergency : UiAction()

    @Serializable
    @SerialName("submit_text")
    data class SubmitText(val text: String) : UiAction()
}
