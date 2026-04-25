package com.example.moasis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.moasis.BuildConfig
import com.example.moasis.ai.melange.MelangeModelManager
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.state.VisionTaskRouter
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.domain.usecase.QuestionAnswerResult
import com.zeticai.mlange.core.model.ModelLoadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmergencyViewModel(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
    private val inferenceOrchestrator: InferenceOrchestrator,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val melangeModelManager: MelangeModelManager? = null,
    private val aiEnabled: Boolean = BuildConfig.AI_ENABLED,
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
            isAiEnabled = aiEnabled,
        )
    )
    val viewState: StateFlow<EmergencyViewState> = _viewState.asStateFlow()

    private var currentDialogueState: DialogueState? = null
    private var pendingImagePaths: List<String> = emptyList()
    private var recentSubmittedImagePaths: List<String> = emptyList()
    private var personalizationJob: Job? = null
    private var questionAnswerJob: Job? = null
    private var aiPreparationJob: Job? = null
    private val personalizedInstructions = mutableMapOf<String, String>()
    private val questionAnswers = mutableMapOf<String, QuestionAnswerResult>()

    init {
        if (aiEnabled && melangeModelManager != null) {
            prepareAiModelIfNeeded()
        }
    }

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
        cancelAiJobs()
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
                cancelAiJobs()
                personalizedInstructions.clear()
                questionAnswers.clear()
                currentDialogueState = null
                _viewState.value = EmergencyViewState(
                    screenMode = ScreenMode.HOME,
                    uiState = UiState(
                        title = "MOASIS",
                        primaryInstruction = "Offline emergency guidance",
                        secondaryInstruction = "Describe what happened to begin.",
                    ),
                    isAiEnabled = aiEnabled,
                    aiStatusText = _viewState.value.aiStatusText,
                    aiProgress = _viewState.value.aiProgress,
                    isAiPreparing = _viewState.value.isAiPreparing,
                    isAiReady = _viewState.value.isAiReady,
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
        val personalizationKey = protocolPersonalizationKey(dialogueState)
        val personalizedInstruction = personalizedInstructions[personalizationKey] ?: step.canonicalText

        if (aiEnabled && personalizedInstructions[personalizationKey] == null) {
            requestProtocolPersonalization(
                dialogueState = dialogueState,
                protocol = protocol,
                step = step,
                cacheKey = personalizationKey,
            )
        }

        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = protocol.title,
                primaryInstruction = personalizedInstruction,
                secondaryInstruction = if (aiEnabled) {
                    "Melange on-device phrasing"
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
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
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
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
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
        val answerKey = questionAnswerKey(dialogueState)
        val answerResult = questionAnswers[answerKey]

        if (aiEnabled && answerResult == null) {
            requestQuestionAnswer(dialogueState, answerKey)
        }

        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = protocol.title,
                primaryInstruction = answerResult?.resumeText ?: step.canonicalText,
                secondaryInstruction = answerResult?.answerText
                    ?: if (aiEnabled) {
                        "Generating an on-device answer for the current step."
                    } else {
                        "Clarification captured. AI responses are disabled in this stage, so the app stays on the current step."
                    },
                warningText = buildWarningText(step),
                visualAids = visualAssetRepository.getAssetsForStep(protocol.protocolId, step.stepId),
                currentStep = dialogueState.stepIndex + 1,
                totalSteps = protocol.steps.size,
                showCallEmergencyButton = protocol.safetyFlags.any { it.contains("emergency_call") },
            ),
            statusText = statusTextOverride ?: answerResult?.fallbackReason ?: if (aiEnabled) {
                "Question received. Melange is preparing a short answer on-device."
            } else {
                "Returning to the current step."
            },
            quickResponses = emptyList(),
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
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
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
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
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
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

    private fun prepareAiModelIfNeeded() {
        val modelManager = melangeModelManager ?: return
        if (!aiEnabled || modelManager.isPreparedInMemory() || aiPreparationJob != null) {
            if (modelManager.isPreparedInMemory()) {
                publishAiState(
                    statusText = "AI model ready on device.",
                    progress = 1f,
                    isPreparing = false,
                    isReady = true,
                )
            }
            return
        }

        val initialStatus = if (modelManager.hasInternetConnection()) {
            "Checking AI model and preparing local runtime."
        } else {
            "Checking whether the AI model is already available on device."
        }
        publishAiState(
            statusText = initialStatus,
            progress = null,
            isPreparing = true,
            isReady = false,
        )

        aiPreparationJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                modelManager.getOrCreateModel(
                    onProgress = { progress ->
                        publishAiState(
                            statusText = "Downloading AI model: ${(progress * 100).toInt()}%",
                            progress = progress.coerceIn(0f, 1f),
                            isPreparing = true,
                            isReady = false,
                        )
                    },
                    onStatusChanged = { status ->
                        val statusText = when (status) {
                            ModelLoadingStatus.UNKNOWN -> "Checking AI model."
                            ModelLoadingStatus.PENDING -> "Preparing AI model."
                            ModelLoadingStatus.DOWNLOADING -> "Downloading AI model."
                            ModelLoadingStatus.TRANSFERRING -> "Finalizing AI model files."
                            ModelLoadingStatus.COMPLETED -> "AI model ready on device."
                            ModelLoadingStatus.FAILED -> "AI model preparation failed."
                            ModelLoadingStatus.CANCELED -> "AI model download canceled."
                            ModelLoadingStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi to continue AI model download."
                            ModelLoadingStatus.NOT_INSTALLED -> "AI model not installed yet. Download will start if network is available."
                            ModelLoadingStatus.REQUIRES_USER_CONFIRMATION -> "AI model download needs user confirmation."
                        }
                        publishAiState(
                            statusText = statusText,
                            progress = if (status == ModelLoadingStatus.COMPLETED) 1f else _viewState.value.aiProgress,
                            isPreparing = status != ModelLoadingStatus.COMPLETED,
                            isReady = status == ModelLoadingStatus.COMPLETED,
                        )
                    },
                )
            }

            if (result.isSuccess) {
                publishAiState(
                    statusText = "AI model ready on device.",
                    progress = 1f,
                    isPreparing = false,
                    isReady = true,
                )
            } else {
                val networkHint = if (modelManager.hasInternetConnection()) {
                    "See the error and retry by relaunching the app."
                } else {
                    "Connect to the internet once to download the model."
                }
                publishAiState(
                    statusText = "AI model is not ready. $networkHint",
                    progress = null,
                    isPreparing = false,
                    isReady = false,
                )
            }
            aiPreparationJob = null
        }
    }

    private fun publishAiState(
        statusText: String?,
        progress: Float?,
        isPreparing: Boolean,
        isReady: Boolean,
    ) {
        _viewState.value = _viewState.value.copy(
            aiStatusText = statusText,
            aiProgress = progress,
            isAiPreparing = isPreparing,
            isAiReady = isReady,
        )
    }

    private fun requestProtocolPersonalization(
        dialogueState: DialogueState.ProtocolMode,
        protocol: com.example.moasis.domain.model.Protocol,
        step: ProtocolStep,
        cacheKey: String,
    ) {
        personalizationJob?.cancel()
        personalizationJob = viewModelScope.launch {
            val response = withContext(Dispatchers.IO) {
                inferenceOrchestrator.personalizeStep(
                    scenarioId = dialogueState.scenarioId,
                    protocol = protocol,
                    step = step,
                    slots = dialogueState.slots,
                    targetListener = dialogueState.slots["patient_type"] ?: "caregiver",
                )
            }
            personalizedInstructions[cacheKey] = response.spokenText

            val currentState = currentDialogueState
            if (currentState is DialogueState.ProtocolMode && protocolPersonalizationKey(currentState) == cacheKey) {
                _viewState.value = _viewState.value.copy(
                    uiState = _viewState.value.uiState.copy(
                        primaryInstruction = response.spokenText,
                    ),
                    statusText = response.fallbackReason ?: _viewState.value.statusText,
                )
            }
        }
    }

    private fun requestQuestionAnswer(
        dialogueState: DialogueState.QuestionMode,
        cacheKey: String,
    ) {
        questionAnswerJob?.cancel()
        questionAnswerJob = viewModelScope.launch {
            val answerResult = withContext(Dispatchers.IO) {
                answerQuestionUseCase.answer(
                    scenarioId = dialogueState.scenarioId,
                    protocolId = dialogueState.protocolId,
                    stepIndex = dialogueState.stepIndex,
                    userQuestion = dialogueState.userQuestion,
                )
            }
            questionAnswers[cacheKey] = answerResult

            val currentState = currentDialogueState
            if (currentState is DialogueState.QuestionMode && questionAnswerKey(currentState) == cacheKey) {
                _viewState.value = _viewState.value.copy(
                    uiState = _viewState.value.uiState.copy(
                        primaryInstruction = answerResult.resumeText,
                        secondaryInstruction = answerResult.answerText,
                    ),
                    statusText = answerResult.fallbackReason ?: "Returning to the current step.",
                )
            }
        }
    }

    private fun protocolPersonalizationKey(dialogueState: DialogueState.ProtocolMode): String {
        val slotsHash = dialogueState.slots.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }
        return "${dialogueState.scenarioId}|${dialogueState.protocolId}|${dialogueState.stepIndex}|$slotsHash"
    }

    private fun questionAnswerKey(dialogueState: DialogueState.QuestionMode): String {
        return "${dialogueState.scenarioId}|${dialogueState.protocolId}|${dialogueState.stepIndex}|${dialogueState.userQuestion}"
    }

    private fun cancelAiJobs() {
        aiPreparationJob?.cancel()
        personalizationJob?.cancel()
        questionAnswerJob?.cancel()
        aiPreparationJob = null
        personalizationJob = null
        questionAnswerJob = null
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

    override fun onCleared() {
        cancelAiJobs()
        super.onCleared()
    }
}

class EmergencyViewModelFactory(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
    private val inferenceOrchestrator: InferenceOrchestrator,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val melangeModelManager: MelangeModelManager? = null,
    private val aiEnabled: Boolean = BuildConfig.AI_ENABLED,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmergencyViewModel::class.java)) {
            return EmergencyViewModel(
                dialogueStateManager = dialogueStateManager,
                protocolRepository = protocolRepository,
                visualAssetRepository = visualAssetRepository,
                inferenceOrchestrator = inferenceOrchestrator,
                answerQuestionUseCase = answerQuestionUseCase,
                melangeModelManager = melangeModelManager,
                aiEnabled = aiEnabled,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
