package com.example.moasis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.moasis.BuildConfig
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.state.VisionTaskRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmergencyViewModel(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
) : ViewModel() {
    private var speechRequestKeyCounter: Int = 0
    private val visionTaskRouter = VisionTaskRouter()
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
    private var pendingImagePaths: List<String> = emptyList()
    private var recentSubmittedImagePaths: List<String> = emptyList()

    fun reduce(event: AppEvent) {
        when (event) {
            is AppEvent.UserTappedAction -> handleAction(event.action)
            is AppEvent.UserSubmittedTurn -> submitText(event.turn.text ?: event.turn.voiceTranscript.orEmpty())
            is AppEvent.VoiceTranscript -> handleVoiceTranscript(event.text, event.isFinal)
            is AppEvent.TtsCompleted -> updateSpeaking(false)
            is AppEvent.TtsInterrupted -> {
                updateSpeaking(false)
                updateStatus(event.reason)
            }
            is AppEvent.LlmCompleted,
            is AppEvent.LlmFailed -> Unit
        }
    }

    fun startEmergency(text: String) {
        submitTurn(text = text)
    }

    fun updateListening(isListening: Boolean) {
        _viewState.value = _viewState.value.copy(
            uiState = _viewState.value.uiState.copy(isListening = isListening),
        )
    }

    fun updateSpeaking(isSpeaking: Boolean) {
        _viewState.value = _viewState.value.copy(
            uiState = _viewState.value.uiState.copy(isSpeaking = isSpeaking),
        )
    }

    fun updateStatus(statusText: String?) {
        _viewState.value = _viewState.value.copy(statusText = statusText)
    }

    fun attachImage(imagePath: String) {
        pendingImagePaths = pendingImagePaths + imagePath
        _viewState.value = _viewState.value.copy(
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            statusText = "Image attached. Submit the turn when ready.",
        )
    }

    fun clearPendingImages() {
        pendingImagePaths = emptyList()
        _viewState.value = _viewState.value.copy(
            attachedImagePaths = recentSubmittedImagePaths,
        )
    }

    fun submitText(text: String) {
        submitTurn(text = text)
    }

    fun submitTurn(text: String = "") {
        if (text.isBlank() && pendingImagePaths.isEmpty()) {
            return
        }

        val submittedImages = pendingImagePaths
        val turn = UserTurn(
            text = text.takeIf { it.isNotBlank() },
            imageUris = submittedImages,
            timestamp = System.currentTimeMillis(),
        )
        val nextStatus = if (submittedImages.isNotEmpty()) {
            val taskType = visionTaskRouter.route(turn, buildTurnContext())
            "Image attached. ${taskType.name} is recognized, but image analysis is not enabled yet."
        } else {
            null
        }

        if (text.isBlank()) {
            pendingImagePaths = emptyList()
            recentSubmittedImagePaths = submittedImages
            _viewState.value = _viewState.value.copy(
                statusText = nextStatus ?: "Image attached. Deterministic guidance continues.",
                attachedImagePaths = recentSubmittedImagePaths,
                transcriptDraft = "",
            )
            return
        }

        val result = dialogueStateManager.handleTurn(
            turn = turn,
            currentState = currentDialogueState,
        )
        currentDialogueState = result.dialogueState
        pendingImagePaths = emptyList()
        recentSubmittedImagePaths = submittedImages
        _viewState.value = when (val dialogueState = result.dialogueState) {
            is DialogueState.ProtocolMode -> buildProtocolViewState(dialogueState, nextStatus)
            is DialogueState.EntryMode -> buildEntryViewState(dialogueState, nextStatus)
            is DialogueState.QuestionMode -> buildQuestionViewState(dialogueState, nextStatus)
            is DialogueState.ReTriageMode -> buildRetriageViewState(dialogueState, nextStatus)
            DialogueState.Completed -> buildCompletedViewState(nextStatus)
        }.copy(
            transcriptDraft = "",
            speechRequestKey = nextSpeechRequestKey(),
            attachedImagePaths = recentSubmittedImagePaths,
        )
    }

    private fun handleAction(action: UiAction) {
        when (action) {
            UiAction.Next -> submitText("next")
            UiAction.Repeat -> refreshCurrentState(
                statusText = "Repeating the current deterministic guidance.",
                forceSpeak = true,
            )
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
                    attachedImagePaths = emptyList(),
                )
                pendingImagePaths = emptyList()
                recentSubmittedImagePaths = emptyList()
            }
            UiAction.CallEmergency -> refreshCurrentState("Call emergency services now if the situation is immediately life-threatening.")
            is UiAction.SubmitText -> submitText(action.text)
        }
    }

    private fun refreshCurrentState(statusText: String, forceSpeak: Boolean = false) {
        _viewState.value = when (val dialogueState = currentDialogueState) {
            is DialogueState.ProtocolMode -> buildProtocolViewState(dialogueState, statusText)
            is DialogueState.EntryMode -> buildEntryViewState(dialogueState, statusText)
            is DialogueState.QuestionMode -> buildQuestionViewState(dialogueState)
            is DialogueState.ReTriageMode -> buildRetriageViewState(dialogueState)
            DialogueState.Completed -> buildCompletedViewState()
            null -> _viewState.value.copy(statusText = statusText)
        }.let {
            if (forceSpeak) {
                it.copy(speechRequestKey = nextSpeechRequestKey())
            } else {
                it
            }
        }
    }

    private fun handleVoiceTranscript(text: String, isFinal: Boolean) {
        if (isFinal) {
            submitText(text)
        } else {
            _viewState.value = _viewState.value.copy(transcriptDraft = text)
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
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
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
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
        )
    }

    private fun buildQuestionViewState(
        dialogueState: DialogueState.QuestionMode,
        statusTextOverride: String? = null,
    ): EmergencyViewState {
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
            statusText = statusTextOverride ?: "Returning to the current deterministic step.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
        )
    }

    private fun buildRetriageViewState(
        dialogueState: DialogueState.ReTriageMode,
        statusTextOverride: String? = null,
    ): EmergencyViewState {
        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = "Re-triage required",
                primaryInstruction = dialogueState.newInput,
                secondaryInstruction = "A higher-priority report interrupted the current step. Continue with the new report from here.",
                warningText = "Leave the current step and reassess immediately.",
                showCallEmergencyButton = true,
            ),
            statusText = statusTextOverride ?: "Current step suspended by a higher-priority change.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
        )
    }

    private fun buildCompletedViewState(statusTextOverride: String? = null): EmergencyViewState {
        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = "Scenario complete",
                primaryInstruction = "No further deterministic steps are pending.",
                secondaryInstruction = "Start a new report if the situation changes.",
            ),
            statusText = statusTextOverride ?: "Deterministic walkthrough finished.",
            quickResponses = emptyList(),
            isAiEnabled = BuildConfig.AI_ENABLED,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
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

    private fun nextSpeechRequestKey(): Int {
        speechRequestKeyCounter += 1
        return speechRequestKeyCounter
    }

    private fun buildTurnContext(): TurnContext {
        return when (val state = currentDialogueState) {
            is DialogueState.EntryMode -> TurnContext(
                dialogueState = state,
                currentProtocolId = null,
                currentStepId = null,
            )
            is DialogueState.ProtocolMode -> {
                val protocol = protocolRepository.getProtocol(state.protocolId)
                val stepId = protocol?.steps?.getOrNull(state.stepIndex)?.stepId
                TurnContext(
                    dialogueState = state,
                    currentProtocolId = state.protocolId,
                    currentStepId = stepId,
                )
            }
            is DialogueState.QuestionMode -> TurnContext(
                dialogueState = state,
                currentProtocolId = state.protocolId,
                currentStepId = protocolRepository.getProtocol(state.protocolId)
                    ?.steps
                    ?.getOrNull(state.stepIndex)
                    ?.stepId,
            )
            is DialogueState.ReTriageMode,
            DialogueState.Completed,
            null -> TurnContext(dialogueState = state)
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
