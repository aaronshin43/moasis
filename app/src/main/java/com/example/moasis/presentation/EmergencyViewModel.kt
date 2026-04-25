package com.example.moasis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.moasis.BuildConfig
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.state.DialogueStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmergencyViewModel(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
) : ViewModel() {
    private val _viewState = MutableStateFlow(
        EmergencyViewState(
            uiState = UiState(
                title = "MOASIS",
                primaryInstruction = "Offline emergency guidance",
                secondaryInstruction = "Describe what happened to begin.",
            ),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    )
    val viewState: StateFlow<EmergencyViewState> = _viewState.asStateFlow()

    private var currentDialogueState: DialogueState? = null

    fun reduce(event: AppEvent) {
        when (event) {
            is AppEvent.UserTappedAction -> handleAction(event.action)
            is AppEvent.UserSubmittedTurn -> submitText(event.turn.text ?: event.turn.voiceTranscript.orEmpty())
            is AppEvent.VoiceTranscript -> if (event.isFinal) submitText(event.text)
            is AppEvent.TtsCompleted,
            is AppEvent.TtsInterrupted,
            is AppEvent.LlmCompleted,
            is AppEvent.LlmFailed -> Unit
        }
    }

    fun startEmergency(text: String) {
        submitText(text)
    }

    fun submitText(text: String) {
        if (text.isBlank()) {
            return
        }

        val result = dialogueStateManager.handleText(
            text = text,
            currentState = currentDialogueState,
        )
        currentDialogueState = result.dialogueState
        _viewState.value = when (val dialogueState = result.dialogueState) {
            is DialogueState.ProtocolMode -> buildProtocolViewState(dialogueState, null)
            is DialogueState.EntryMode -> buildEntryViewState(dialogueState, null)
            is DialogueState.QuestionMode -> buildQuestionViewState(dialogueState)
            is DialogueState.ReTriageMode -> buildRetriageViewState(dialogueState)
            DialogueState.Completed -> buildCompletedViewState()
        }
    }

    private fun handleAction(action: UiAction) {
        when (action) {
            UiAction.Next -> submitText("next")
            UiAction.Repeat -> refreshCurrentState("Repeating the current deterministic guidance.")
            UiAction.Back -> {
                currentDialogueState = null
                _viewState.value = EmergencyViewState(
                    screenMode = ScreenMode.HOME,
                    uiState = UiState(
                        title = "MOASIS",
                        primaryInstruction = "Offline emergency guidance",
                        secondaryInstruction = "Describe what happened to begin.",
                    ),
                    isAiEnabled = BuildConfig.AI_ENABLED,
                )
            }
            UiAction.CallEmergency -> refreshCurrentState("Call emergency services now if the situation is immediately life-threatening.")
            is UiAction.SubmitText -> submitText(action.text)
        }
    }

    private fun refreshCurrentState(statusText: String) {
        _viewState.value = when (val dialogueState = currentDialogueState) {
            is DialogueState.ProtocolMode -> buildProtocolViewState(dialogueState, statusText)
            is DialogueState.EntryMode -> buildEntryViewState(dialogueState, statusText)
            is DialogueState.QuestionMode -> buildQuestionViewState(dialogueState)
            is DialogueState.ReTriageMode -> buildRetriageViewState(dialogueState)
            DialogueState.Completed -> buildCompletedViewState()
            null -> _viewState.value.copy(statusText = statusText)
        }
    }

    private fun buildProtocolViewState(
        dialogueState: DialogueState.ProtocolMode,
        statusText: String?,
    ): EmergencyViewState {
        val protocol = requireNotNull(protocolRepository.getProtocol(dialogueState.protocolId)) {
            "Missing protocol for ${dialogueState.protocolId}"
        }
        val step = requireNotNull(protocol.steps.getOrNull(dialogueState.stepIndex)) {
            "Missing step ${dialogueState.stepIndex} for ${dialogueState.protocolId}"
        }
        val warningText = buildWarningText(step)

        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = protocol.title,
                primaryInstruction = step.canonicalText,
                secondaryInstruction = if (BuildConfig.AI_ENABLED) {
                    "AI-enhanced phrasing enabled"
                } else {
                    "Deterministic guidance mode"
                },
                warningText = warningText,
                visualAids = visualAssetRepository.getAssetsForStep(protocol.protocolId, step.stepId),
                currentStep = dialogueState.stepIndex + 1,
                totalSteps = protocol.steps.size,
                isSpeaking = dialogueState.isSpeaking,
                showCallEmergencyButton = protocol.safetyFlags.any { it.contains("emergency_call") },
            ),
            statusText = statusText,
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    }

    private fun buildEntryViewState(
        dialogueState: DialogueState.EntryMode,
        statusText: String?,
    ): EmergencyViewState {
        val tree = requireNotNull(protocolRepository.getTree(dialogueState.treeId)) {
            "Missing tree for ${dialogueState.treeId}"
        }
        val node = requireNotNull(tree.nodes.firstOrNull { it.id == dialogueState.nodeId }) {
            "Missing node ${dialogueState.nodeId} for ${dialogueState.treeId}"
        }

        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = treeTitle(dialogueState.treeId),
                primaryInstruction = node.prompt ?: "Answer the next question.",
                secondaryInstruction = "Use the quickest accurate answer you can.",
                currentStep = 0,
                totalSteps = 0,
                showCallEmergencyButton = dialogueState.treeId == "collapsed_person_entry",
            ),
            statusText = statusText,
            quickResponses = if (node.type == "question") listOf("Yes", "No") else emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    }

    private fun buildQuestionViewState(dialogueState: DialogueState.QuestionMode): EmergencyViewState {
        val protocol = requireNotNull(protocolRepository.getProtocol(dialogueState.protocolId)) {
            "Missing protocol for ${dialogueState.protocolId}"
        }
        val step = requireNotNull(protocol.steps.getOrNull(dialogueState.stepIndex)) {
            "Missing step ${dialogueState.stepIndex} for ${dialogueState.protocolId}"
        }

        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = protocol.title,
                primaryInstruction = step.canonicalText,
                secondaryInstruction = "Clarification captured. AI responses are disabled in this stage, so the app stays on the current step.",
                warningText = buildWarningText(step),
                visualAids = visualAssetRepository.getAssetsForStep(protocol.protocolId, step.stepId),
                currentStep = dialogueState.stepIndex + 1,
                totalSteps = protocol.steps.size,
                showCallEmergencyButton = protocol.safetyFlags.any { it.contains("emergency_call") },
            ),
            statusText = "Returning to the current deterministic step.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    }

    private fun buildRetriageViewState(dialogueState: DialogueState.ReTriageMode): EmergencyViewState {
        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = "Re-triage required",
                primaryInstruction = dialogueState.newInput,
                secondaryInstruction = "A higher-priority report interrupted the current step. Continue with the new report from here.",
                warningText = "Leave the current step and reassess immediately.",
                showCallEmergencyButton = true,
            ),
            statusText = "Current step suspended by a higher-priority change.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    }

    private fun buildCompletedViewState(): EmergencyViewState {
        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = "Scenario complete",
                primaryInstruction = "No further deterministic steps are pending.",
                secondaryInstruction = "Start a new report if the situation changes.",
            ),
            statusText = "Deterministic walkthrough finished.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
        )
    }

    private fun buildWarningText(step: ProtocolStep): String? {
        if (step.forbiddenKeywords.isEmpty()) {
            return null
        }
        return "Avoid: ${step.forbiddenKeywords.joinToString(", ")}"
    }

    private fun treeTitle(treeId: String): String {
        return when (treeId) {
            "collapsed_person_entry" -> "Collapsed Person Triage"
            "entry_general_emergency" -> "General Emergency Triage"
            "burn_tree" -> "Burn Triage"
            "bleeding_tree" -> "Bleeding Triage"
            else -> treeId.replace('_', ' ')
        }
    }
}

class EmergencyViewModelFactory(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmergencyViewModel::class.java)) {
            return EmergencyViewModel(
                dialogueStateManager = dialogueStateManager,
                protocolRepository = protocolRepository,
                visualAssetRepository = visualAssetRepository,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
