package com.example.moasis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.moasis.BuildConfig
import com.example.moasis.ai.melange.classifyAiPreparationFailure
import com.example.moasis.ai.melange.MelangeModelManager
import com.example.moasis.ai.melange.MelangeVisionModelManager
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.ai.orchestrator.VisionDetectionEngine
import com.example.moasis.data.local.SessionArchiveMessage
import com.example.moasis.data.local.SessionRepository
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.EmergencySessionDetail
import com.example.moasis.domain.model.EmergencySessionSummary
import com.example.moasis.domain.model.FactSource
import com.example.moasis.domain.model.ObservedFact
import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.model.VisualAid
import com.example.moasis.domain.model.VisionTaskType
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.state.ObjectPresenceQueryParser
import com.example.moasis.domain.state.ObservationMerger
import com.example.moasis.domain.state.VisionTaskRouter
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.domain.usecase.QuestionAnswerResult
import com.zeticai.mlange.core.model.ModelLoadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class EmergencyViewModel(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
    private val inferenceOrchestrator: InferenceOrchestrator,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val sessionRepository: SessionRepository? = null,
    private val melangeModelManager: MelangeModelManager? = null,
    private val melangeVisionModelManager: MelangeVisionModelManager? = null,
    private val visionDetectionEngine: VisionDetectionEngine? = null,
    private val embeddingPreparationStateHolder: EmbeddingPreparationStateHolder? = null,
    private val aiEnabled: Boolean = BuildConfig.AI_ENABLED,
) : ViewModel() {
    private var speechRequestKeyCounter: Int = 0
    private val visionTaskRouter = VisionTaskRouter()
    private val objectPresenceQueryParser = ObjectPresenceQueryParser()
    private val observationMerger = ObservationMerger()
    private val _viewState = MutableStateFlow(
        EmergencyViewState(
            uiState = UiState(
                title = "MOASIS",
                primaryInstruction = "Offline emergency guidance",
                secondaryInstruction = "Describe what happened to begin.",
            ),
            isAiEnabled = aiEnabled,
            isEmbeddingEnabled = embeddingPreparationStateHolder?.currentState()?.isEnabled == true,
            embeddingStatusText = embeddingPreparationStateHolder?.currentState()?.statusText,
            isEmbeddingPreparing = embeddingPreparationStateHolder?.currentState()?.isPreparing == true,
            isEmbeddingReady = embeddingPreparationStateHolder?.currentState()?.isReady == true,
            aiModelLabel = melangeModelManager?.configuredModelLabel(),
            aiCacheSummaryText = melangeModelManager?.inspectCache()?.summaryText(),
        )
    )
    val viewState: StateFlow<EmergencyViewState> = _viewState.asStateFlow()

    private var currentDialogueState: DialogueState? = null
    private var pendingImagePaths: List<String> = emptyList()
    private var recentSubmittedImagePaths: List<String> = emptyList()
    private var currentSessionId: String? = null
    private var currentSessionStartedAtMs: Long? = null
    private var lastSessionProtocolId: String? = null
    private var lastSessionProtocolTitle: String? = null
    private var lastSessionProtocolCategory: String? = null
    private var viewedArchivedSessionId: String? = null
    private var earlierSessionsFromDb: List<EmergencySessionSummary> = emptyList()
    private var personalizationJob: Job? = null
    private var questionAnswerJob: Job? = null
    private var aiPreparationJob: Job? = null
    private var visionPreparationJob: Job? = null
    private var visionDetectionJob: Job? = null
    private val personalizedInstructions = mutableMapOf<String, String>()
    private val questionAnswers = mutableMapOf<String, QuestionAnswerResult>()

    init {
        sessionRepository?.let { repository ->
            viewModelScope.launch {
                repository.observeEarlierSessions().collectLatest { sessions ->
                    earlierSessionsFromDb = sessions
                    publishEarlierSessions()
                }
            }
        }
        embeddingPreparationStateHolder?.let { holder ->
            viewModelScope.launch {
                holder.state.collectLatest { state ->
                    _viewState.value = _viewState.value.copy(
                        isEmbeddingEnabled = state.isEnabled,
                        embeddingStatusText = state.statusText,
                        isEmbeddingPreparing = state.isPreparing,
                        isEmbeddingReady = state.isReady,
                    )
                }
            }
        }
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

    fun resetSession() {
        cancelResponseJobs()
        personalizedInstructions.clear()
        questionAnswers.clear()
        currentDialogueState = null
        pendingImagePaths = emptyList()
        recentSubmittedImagePaths = emptyList()
        currentSessionId = null
        currentSessionStartedAtMs = null
        lastSessionProtocolId = null
        lastSessionProtocolTitle = null
        lastSessionProtocolCategory = null
        viewedArchivedSessionId = null
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
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isEmbeddingEnabled = _viewState.value.isEmbeddingEnabled,
            embeddingStatusText = _viewState.value.embeddingStatusText,
            isEmbeddingPreparing = _viewState.value.isEmbeddingPreparing,
            isEmbeddingReady = _viewState.value.isEmbeddingReady,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            chatHistory = emptyList(),
            recentObservedFacts = emptyList(),
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = false,
            viewingArchivedSessionId = null,
        )
        publishEarlierSessions()
    }

    fun startNewSession() {
        val snapshot = buildCurrentSessionArchiveSnapshot()
        resetSession()
        val repository = sessionRepository ?: return
        if (snapshot != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.archiveSessionSnapshot(snapshot)
            }
        }
    }

    fun deleteEarlierSession(sessionId: String) {
        val repository = sessionRepository ?: return
        val shouldClearViewedSession = _viewState.value.isViewingArchivedSession && viewedArchivedSessionId == sessionId
        if (shouldClearViewedSession) {
            resetSession()
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(sessionId)
        }
    }

    fun openEarlierSession(sessionId: String) {
        val repository = sessionRepository ?: return
        val currentSnapshot = buildCurrentSessionArchiveSnapshot()
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                if (currentSnapshot != null && currentSnapshot.sessionId != sessionId) {
                    repository.archiveSessionSnapshot(currentSnapshot)
                }
                repository.getSessionDetail(sessionId)
            } ?: return@launch
            cancelResponseJobs()
            currentDialogueState = null
            pendingImagePaths = emptyList()
            recentSubmittedImagePaths = emptyList()
            currentSessionId = null
            currentSessionStartedAtMs = null
            lastSessionProtocolId = null
            lastSessionProtocolTitle = null
            lastSessionProtocolCategory = null
            viewedArchivedSessionId = sessionId
            _viewState.value = _viewState.value.copy(
                screenMode = ScreenMode.ACTIVE,
                uiState = UiState(
                    title = detail.summary.title,
                    primaryInstruction = "Saved session",
                    secondaryInstruction = "Read-only history",
                    guidanceOriginLabel = "Archived session",
                ),
                statusText = "Viewing a saved session. Start a new session to continue emergency guidance.",
                quickResponses = emptyList(),
                transcriptDraft = "",
                attachedImagePaths = emptyList(),
                chatHistory = detail.toChatHistory(),
                isViewingArchivedSession = true,
                viewingArchivedSessionId = sessionId,
            )
            publishEarlierSessions()
        }
    }

    fun autosaveCurrentSession() {
        val snapshot = buildCurrentSessionArchiveSnapshot() ?: return
        val repository = sessionRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.archiveSessionSnapshot(snapshot)
        }
    }

    fun retryAiPreparation() {
        prepareAiModelIfNeeded(force = true)
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        _viewState.value = _viewState.value.copy(isOfflineModeEnabled = enabled)
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

    fun removeAttachedImage(imagePath: String) {
        pendingImagePaths = pendingImagePaths.filterNot { it == imagePath }
        recentSubmittedImagePaths = recentSubmittedImagePaths.filterNot { it == imagePath }
        _viewState.value = _viewState.value.copy(
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            statusText = if (pendingImagePaths.isEmpty() && recentSubmittedImagePaths.isEmpty()) {
                null
            } else {
                _viewState.value.statusText
            },
        )
    }

    fun clearSessionArtifacts() {
        pendingImagePaths = emptyList()
        recentSubmittedImagePaths = emptyList()
        _viewState.value = _viewState.value.copy(
            transcriptDraft = "",
            attachedImagePaths = emptyList(),
            chatHistory = emptyList(),
            recentObservedFacts = emptyList(),
            statusText = "Photos and transcripts cleared from this session.",
        )
    }

    fun submitText(text: String) {
        submitTurn(text = text)
    }

    fun submitTurn(text: String = "") {
        if (isInputBlockedUntilAiReady()) {
            _viewState.value = _viewState.value.copy(
                statusText = _viewState.value.aiStatusText ?: "Wait for the AI model to finish loading before starting.",
            )
            return
        }
        if (text.isBlank() && pendingImagePaths.isEmpty()) {
            return
        }

        val submittedAtMs = System.currentTimeMillis()
        ensureCurrentSessionStarted(submittedAtMs)
        val submittedImages = pendingImagePaths
        val turn = UserTurn(
            text = text.takeIf { it.isNotBlank() },
            imageUris = submittedImages,
            timestamp = submittedAtMs,
        )
        val nextStatus = if (submittedImages.isNotEmpty()) {
            val taskType = visionTaskRouter.route(turn, buildTurnContext())
            if (isVisionTaskSupported(taskType)) {
                val detectorManager = melangeVisionModelManager
                if (detectorManager == null || !detectorManager.isConfigured()) {
                    "Image attached. ${visionTaskLabel(taskType)} is supported, but the YOLO detector is not configured yet."
                } else {
                    requestVisionDetection(
                        imagePath = submittedImages.last(),
                        taskType = taskType,
                        userText = text,
                    )
                    if (detectorManager.isPreparedInMemory()) {
                        "Image attached. Running the YOLO detector for ${visionTaskLabel(taskType).lowercase()}."
                    } else {
                        "Image attached. Preparing the YOLO detector for ${visionTaskLabel(taskType).lowercase()}."
                    }
                }
            } else {
                "Image attached. Vision is currently limited to kit inventory and simple object-presence checks."
            }
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

        // Archive the current assistant response (if active) + user message into history.
        val prevHistory = _viewState.value.chatHistory
        val updatedHistory = buildUpdatedHistory(prevHistory, text)

        val result = dialogueStateManager.handleTurn(
            turn = turn,
            currentState = currentDialogueState,
        )
        cancelResponseJobs()
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
            chatHistory = updatedHistory,
        )
    }

    private fun buildUpdatedHistory(prevHistory: List<ChatMessage>, userText: String): List<ChatMessage> {
        val current = _viewState.value
        val archivedAssistant: List<ChatMessage> = if (
            current.screenMode == ScreenMode.ACTIVE &&
            current.uiState.primaryInstruction.isNotBlank()
        ) {
            val us = current.uiState
            listOf(
                ChatMessage.Assistant(
                    title = us.title,
                    primaryInstruction = us.primaryInstruction,
                    secondaryInstruction = us.secondaryInstruction,
                    warningText = us.warningText,
                    visualAids = us.visualAids,
                    currentStep = us.currentStep,
                    totalSteps = us.totalSteps,
                )
            )
        } else {
            emptyList()
        }
        return prevHistory + archivedAssistant + ChatMessage.User(userText)
    }

    private fun ensureCurrentSessionStarted(startedAtMs: Long) {
        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString()
            currentSessionStartedAtMs = startedAtMs
            publishEarlierSessions()
        }
    }

    private fun publishEarlierSessions() {
        val hiddenSessionIds = setOfNotNull(currentSessionId)
        _viewState.value = _viewState.value.copy(
            earlierSessions = earlierSessionsFromDb.filterNot { it.sessionId in hiddenSessionIds },
        )
    }

    private fun buildCurrentSessionArchiveSnapshot(): SessionArchiveSnapshot? {
        val sessionId = currentSessionId ?: return null
        val createdAtMs = currentSessionStartedAtMs ?: return null
        val current = _viewState.value
        if (current.screenMode == ScreenMode.HOME && current.chatHistory.isEmpty()) {
            return null
        }

        val protocol = when (val state = currentDialogueState) {
            is DialogueState.ProtocolMode -> protocolRepository.getProtocol(state.protocolId)
            is DialogueState.QuestionMode -> protocolRepository.getProtocol(state.protocolId)
            else -> lastSessionProtocolId?.let { protocolRepository.getProtocol(it) }
        }
        val updatedAtMs = System.currentTimeMillis().coerceAtLeast(createdAtMs)
        return SessionArchiveSnapshot(
            sessionId = sessionId,
            title = protocol?.title
                ?: lastSessionProtocolTitle
                ?: current.uiState.title.ifBlank { "Emergency session" },
            category = protocol?.category ?: lastSessionProtocolCategory,
            protocolId = protocol?.protocolId ?: lastSessionProtocolId,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            lastStepIndex = current.uiState.currentStep.takeIf { it > 0 },
            totalSteps = current.uiState.totalSteps.takeIf { it > 0 },
            messages = buildArchiveMessages(current),
        )
    }

    private fun buildArchiveMessages(current: EmergencyViewState): List<SessionArchiveMessage> {
        val historyMessages = current.chatHistory.map { message ->
            when (message) {
                is ChatMessage.User -> SessionArchiveMessage(
                    role = "user",
                    title = null,
                    text = message.text,
                    secondaryText = null,
                    warningText = null,
                    visualAidIds = emptyList(),
                )
                is ChatMessage.Assistant -> SessionArchiveMessage(
                    role = "assistant",
                    title = message.title,
                    text = message.primaryInstruction,
                    secondaryText = message.secondaryInstruction,
                    warningText = message.warningText,
                    visualAidIds = message.visualAids.map { it.assetId },
                )
            }
        }
        val activeAssistant = if (
            current.screenMode == ScreenMode.ACTIVE &&
            current.uiState.primaryInstruction.isNotBlank()
        ) {
            listOf(
                SessionArchiveMessage(
                    role = "assistant",
                    title = current.uiState.title,
                    text = current.uiState.primaryInstruction,
                    secondaryText = current.uiState.secondaryInstruction,
                    warningText = current.uiState.warningText,
                    visualAidIds = current.uiState.visualAids.map { it.assetId },
                )
            )
        } else {
            emptyList()
        }
        return historyMessages + activeAssistant
    }

    private fun EmergencySessionDetail.toChatHistory(): List<ChatMessage> {
        return messages.map { message ->
            if (message.role == "user") {
                ChatMessage.User(message.text)
            } else {
                ChatMessage.Assistant(
                    title = message.title ?: summary.title,
                    primaryInstruction = message.text,
                    secondaryInstruction = message.secondaryText,
                    warningText = message.warningText,
                    visualAids = message.visualAidIds.mapNotNull { assetId ->
                        visualAssetRepository.resolveAsset(assetId)?.let { entry ->
                            VisualAid(
                                assetId = entry.assetId,
                                type = entry.type,
                                caption = entry.caption,
                                contentDescription = entry.contentDescription,
                            )
                        }
                    },
                    currentStep = 0,
                    totalSteps = 0,
                )
            }
        }
    }

    private fun handleAction(action: UiAction) {
        when (action) {
            UiAction.Next -> submitText("next")
            UiAction.Repeat -> refreshCurrentState(
                statusText = "Repeating the current deterministic guidance.",
                forceSpeak = true,
            )
            UiAction.RetryAiPreparation -> retryAiPreparation()
            UiAction.Back -> resetSession()
            UiAction.CallEmergency -> refreshCurrentState("Call emergency services now if the situation is immediately life-threatening.")
            is UiAction.SubmitText -> submitText(action.text)
        }
    }

    private fun refreshCurrentState(statusText: String, forceSpeak: Boolean = false) {
        val preservedHistory = _viewState.value.chatHistory
        _viewState.value = when (val dialogueState = currentDialogueState) {
            is DialogueState.ProtocolMode -> buildProtocolViewState(dialogueState, statusText)
            is DialogueState.EntryMode -> buildEntryViewState(dialogueState, statusText)
            is DialogueState.QuestionMode -> buildQuestionViewState(dialogueState)
            is DialogueState.ReTriageMode -> buildRetriageViewState(dialogueState)
            DialogueState.Completed -> buildCompletedViewState()
            null -> _viewState.value.copy(statusText = statusText)
        }.let {
            val base = it.copy(chatHistory = preservedHistory)
            if (forceSpeak) base.copy(speechRequestKey = nextSpeechRequestKey()) else base
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
        rememberSessionProtocol(protocol)
        val step = requireNotNull(protocol.steps.getOrNull(dialogueState.stepIndex)) {
            "Missing step ${dialogueState.stepIndex} for ${dialogueState.protocolId}"
        }
        val warningText = buildWarningText(step)
        val personalizationKey = protocolPersonalizationKey(dialogueState)
        val hasPersonalizedInstruction = personalizedInstructions[personalizationKey] != null
        val personalizedInstruction = personalizedInstructions[personalizationKey] ?: step.canonicalText

        if (aiEnabled && !hasPersonalizedInstruction) {
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
                title = if (aiEnabled && !hasPersonalizedInstruction) "" else protocol.title,
                primaryInstruction = if (aiEnabled && !hasPersonalizedInstruction) "" else personalizedInstruction,
                secondaryInstruction = null,
                guidanceOriginLabel = protocolGuidanceOriginLabel(
                    hasPersonalizedInstruction = hasPersonalizedInstruction,
                ),
                warningText = if (aiEnabled && !hasPersonalizedInstruction) null else warningText,
                visualAids = if (aiEnabled && !hasPersonalizedInstruction) {
                    emptyList()
                } else {
                    visualAssetRepository.getAssetsForStep(protocol.protocolId, step.stepId)
                },
                currentStep = if (aiEnabled && !hasPersonalizedInstruction) 0 else dialogueState.stepIndex + 1,
                totalSteps = if (aiEnabled && !hasPersonalizedInstruction) 0 else protocol.steps.size,
                isSpeaking = dialogueState.isSpeaking,
                isAiAnswerPending = aiEnabled && !hasPersonalizedInstruction,
            ),
            statusText = statusText,
            quickResponses = emptyList(),
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            recentObservedFacts = _viewState.value.recentObservedFacts,
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = _viewState.value.isViewingArchivedSession,
            viewingArchivedSessionId = _viewState.value.viewingArchivedSessionId,
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
                title = "",
                primaryInstruction = node.prompt ?: "Answer the next question.",
                secondaryInstruction = null,
                guidanceOriginLabel = null,
                currentStep = 0,
                totalSteps = 0,
            ),
            statusText = statusText,
            quickResponses = if (node.type == "question") listOf("Yes", "No") else emptyList(),
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            recentObservedFacts = _viewState.value.recentObservedFacts,
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = _viewState.value.isViewingArchivedSession,
            viewingArchivedSessionId = _viewState.value.viewingArchivedSessionId,
        )
    }

    private fun buildQuestionViewState(
        dialogueState: DialogueState.QuestionMode,
        statusTextOverride: String? = null,
    ): EmergencyViewState {
        val protocol = requireNotNull(protocolRepository.getProtocol(dialogueState.protocolId)) {
            "Missing protocol for ${dialogueState.protocolId}"
        }
        rememberSessionProtocol(protocol)
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
                title = "",
                primaryInstruction = answerResult?.answerText.orEmpty(),
                secondaryInstruction = answerResult?.resumeText?.takeIf { it.isNotBlank() }?.let {
                    "Continue with the current step."
                },
                guidanceOriginLabel = null,
                warningText = null,
                visualAids = emptyList(),
                currentStep = 0,
                totalSteps = 0,
                isAiAnswerPending = aiEnabled && answerResult == null,
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
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            recentObservedFacts = _viewState.value.recentObservedFacts,
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = _viewState.value.isViewingArchivedSession,
            viewingArchivedSessionId = _viewState.value.viewingArchivedSessionId,
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
                guidanceOriginLabel = "Deterministic re-triage",
                warningText = "Leave the current step and reassess immediately.",
            ),
            statusText = statusTextOverride ?: "Current step suspended by a higher-priority change.",
            quickResponses = emptyList(),
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            recentObservedFacts = _viewState.value.recentObservedFacts,
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = _viewState.value.isViewingArchivedSession,
            viewingArchivedSessionId = _viewState.value.viewingArchivedSessionId,
        )
    }

    private fun buildCompletedViewState(statusTextOverride: String? = null): EmergencyViewState {
        return EmergencyViewState(
            screenMode = ScreenMode.ACTIVE,
            uiState = UiState(
                title = lastSessionProtocolTitle ?: "Scenario complete",
                primaryInstruction = "No further deterministic steps are pending.",
                secondaryInstruction = "Start a new report if the situation changes.",
                guidanceOriginLabel = "Deterministic completion state",
            ),
            statusText = statusTextOverride ?: "Deterministic walkthrough finished.",
            quickResponses = emptyList(),
            isAiEnabled = aiEnabled,
            aiStatusText = _viewState.value.aiStatusText,
            aiProgress = _viewState.value.aiProgress,
            isAiPreparing = _viewState.value.isAiPreparing,
            isAiReady = _viewState.value.isAiReady,
            canRetryAiPreparation = _viewState.value.canRetryAiPreparation,
            isOfflineModeEnabled = _viewState.value.isOfflineModeEnabled,
            aiModelLabel = _viewState.value.aiModelLabel,
            aiRouteText = _viewState.value.aiRouteText,
            aiCacheSummaryText = _viewState.value.aiCacheSummaryText,
            aiDiagnosticDetail = _viewState.value.aiDiagnosticDetail,
            transcriptDraft = _viewState.value.transcriptDraft,
            attachedImagePaths = pendingImagePaths.ifEmpty { recentSubmittedImagePaths },
            recentObservedFacts = _viewState.value.recentObservedFacts,
            earlierSessions = _viewState.value.earlierSessions,
            isViewingArchivedSession = _viewState.value.isViewingArchivedSession,
            viewingArchivedSessionId = _viewState.value.viewingArchivedSessionId,
        )
    }

    private fun buildWarningText(step: ProtocolStep): String? {
        if (step.forbiddenKeywords.isEmpty()) {
            return null
        }
        return "Avoid: ${step.forbiddenKeywords.joinToString(", ")}"
    }

    private fun rememberSessionProtocol(protocol: Protocol) {
        lastSessionProtocolId = protocol.protocolId
        lastSessionProtocolTitle = protocol.title
        lastSessionProtocolCategory = protocol.category
    }

    private fun isInputBlockedUntilAiReady(): Boolean {
        return (aiEnabled && !_viewState.value.isAiReady) ||
            (_viewState.value.isEmbeddingEnabled && !_viewState.value.isEmbeddingReady)
    }

    private fun treeTitle(treeId: String): String {
        return when (treeId) {
            "collapsed_person_entry" -> "Collapsed Person Triage"
            "entry_general_emergency" -> "General Emergency Triage"
            "general_assessment_tree" -> "General assessment"
            "burn_tree" -> "Burn Triage"
            "bleeding_tree" -> "Bleeding Triage"
            "breathing_problem_tree" -> "Breathing Trouble Triage"
            "choking_tree" -> "Choking Triage"
            else -> treeId.replace('_', ' ')
        }
    }

    private fun nextSpeechRequestKey(): Int {
        speechRequestKeyCounter += 1
        return speechRequestKeyCounter
    }

    private fun prepareAiModelIfNeeded(force: Boolean = false) {
        val modelManager = melangeModelManager ?: return
        if (!aiEnabled) {
            return
        }
        if (!modelManager.isLikelySupportedAbi()) {
            publishAiState(
                statusText = "This device ABI is not supported for Melange local runtime.",
                progress = null,
                isPreparing = false,
                isReady = false,
                canRetry = false,
                routeText = "Runtime ABI compatibility check",
                diagnosticDetail = null,
            )
            return
        }
        if (force) {
            aiPreparationJob?.cancel()
            aiPreparationJob = null
        }
        if (modelManager.isPreparedInMemory() || aiPreparationJob != null) {
            if (modelManager.isPreparedInMemory()) {
                publishAiState(
                    statusText = "AI model ready on device.",
                    progress = 1f,
                    isPreparing = false,
                    isReady = true,
                    canRetry = false,
                    routeText = "In-memory model reuse",
                    diagnosticDetail = null,
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
            canRetry = false,
            routeText = "Startup cache and model check",
            diagnosticDetail = null,
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
                            canRetry = false,
                            routeText = if (_viewState.value.aiRouteText == "Play Asset Delivery lookup") {
                                "Play Asset Delivery download"
                            } else {
                                "Direct Melange metadata or model download"
                            },
                            diagnosticDetail = null,
                        )
                    },
                    onStatusChanged = { status ->
                        val isTransientBackendFailure =
                            status == ModelLoadingStatus.FAILED && !modelManager.isPreparedInMemory()

                        val statusText = when {
                            isTransientBackendFailure ->
                                "One local accelerator path failed. Trying an alternate runtime."
                            else -> when (status) {
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
                        }
                        publishAiState(
                            statusText = statusText,
                            progress = if (status == ModelLoadingStatus.COMPLETED) 1f else _viewState.value.aiProgress,
                            isPreparing = status != ModelLoadingStatus.COMPLETED,
                            isReady = status == ModelLoadingStatus.COMPLETED,
                            canRetry = when {
                                isTransientBackendFailure -> false
                                else -> status == ModelLoadingStatus.FAILED || status == ModelLoadingStatus.CANCELED || status == ModelLoadingStatus.WAITING_FOR_WIFI
                            },
                            routeText = statusToRouteText(
                                status = status,
                                treatFailedAsTransient = isTransientBackendFailure,
                            ),
                            diagnosticDetail = _viewState.value.aiDiagnosticDetail,
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
                    canRetry = false,
                    routeText = _viewState.value.aiRouteText ?: "Local model ready",
                    diagnosticDetail = null,
                )
            } else {
                val failure = classifyAiPreparationFailure(
                    throwable = requireNotNull(result.exceptionOrNull()),
                    hasInternetConnection = modelManager.hasInternetConnection(),
                )
                publishAiState(
                    statusText = failure.userMessage,
                    progress = null,
                    isPreparing = false,
                    isReady = false,
                    canRetry = failure.canRetry,
                    routeText = classifyFailureRouteText(requireNotNull(result.exceptionOrNull())),
                    diagnosticDetail = failure.detail,
                )
            }
            aiPreparationJob = null
        }
    }

    private fun prepareVisionModelIfNeeded(force: Boolean = false) {
        val modelManager = melangeVisionModelManager ?: return
        if (!modelManager.isConfigured()) {
            return
        }
        if (force) {
            visionPreparationJob?.cancel()
            visionPreparationJob = null
        }
        if (modelManager.isPreparedInMemory() || visionPreparationJob != null) {
            return
        }

        visionPreparationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = modelManager.getOrCreateSession()
            result
                .onSuccess {
                    Log.d(TAG, "YOLO detector ready on demand.")
                }
                .onFailure { throwable ->
                    Log.w(TAG, "YOLO detector lazy init failed: ${throwable.message ?: throwable::class.java.simpleName}")
                }
            visionPreparationJob = null
        }
    }

    private fun requestVisionDetection(
        imagePath: String,
        taskType: VisionTaskType,
        userText: String,
    ) {
        val engine = visionDetectionEngine ?: run {
            Log.w(TAG, "YOLO detector requested without an engine.")
            return
        }
        visionDetectionJob?.cancel()
        visionDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            val result = engine.detect(imagePath = imagePath, taskType = taskType)
            result
                .onSuccess { detection ->
                    val facts = detection.objects
                        .distinctBy { it.label }
                        .map { detected ->
                            ObservedFact(
                                key = observedFactKeyFor(taskType),
                                value = detected.label,
                                confidence = detected.confidence,
                                source = FactSource.VISION_SUGGESTED,
                                evidence = taskType.name,
                            )
                        }
                    val mergedFacts = observationMerger.merge(
                        turn = UserTurn(imageUris = listOf(imagePath), timestamp = System.currentTimeMillis()),
                        existingFacts = _viewState.value.recentObservedFacts,
                        newFacts = facts,
                    )
                    val summary = buildVisionSummary(
                        taskType = taskType,
                        userText = userText,
                        facts = mergedFacts,
                        rawLabels = detection.rawObjects.map { it.label }.distinct(),
                    )
                    _viewState.value = _viewState.value.copy(
                        statusText = summary,
                        recentObservedFacts = mergedFacts,
                    )
                }
                .onFailure { throwable ->
                    Log.w(TAG, "YOLO detection failed: ${throwable.message ?: throwable::class.java.simpleName}")
                    _viewState.value = _viewState.value.copy(
                        statusText = "YOLO detection failed for this image. Deterministic guidance will continue.",
                    )
                }
            visionDetectionJob = null
        }
    }

    private fun buildVisionSummary(
        taskType: VisionTaskType,
        userText: String,
        facts: List<ObservedFact>,
        rawLabels: List<String>,
    ): String {
        val labels = facts.map { it.value }.distinct()
        if (labels.isEmpty()) {
            if (taskType == VisionTaskType.KIT_DETECTION && rawLabels.isNotEmpty()) {
                return "The YOLO kit scan did not find any supported kit labels. Generic objects seen: ${rawLabels.joinToString(", ")}."
            }
            return "YOLO detector did not find any supported common objects in the attached image."
        }

        if (taskType == VisionTaskType.OBJECT_PRESENCE_CHECK) {
            val query = objectPresenceQueryParser.parse(userText)
            if (query != null) {
                val matched = labels.any { it.equals(query.canonicalLabel, ignoreCase = true) }
                return if (matched) {
                    "Yes. I can see ${articleFor(query.spokenLabel)} ${query.spokenLabel} in the image."
                } else {
                    "I do not see ${articleFor(query.spokenLabel)} ${query.spokenLabel} in the image. Detected objects: ${labels.joinToString(", ")}."
                }
            }
            return "I could not map that object request yet. Detected objects: ${labels.joinToString(", ")}."
        }

        return when (taskType) {
            VisionTaskType.KIT_DETECTION ->
                "Detected common kit-visible items: ${labels.joinToString(", ")}."
            VisionTaskType.STEP_VERIFICATION ->
                "Detected visible objects for step check: ${labels.joinToString(", ")}."
            else ->
                "Detected objects: ${labels.joinToString(", ")}."
        }
    }

    private fun articleFor(word: String): String {
        return if (word.firstOrNull()?.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
    }

    private fun observedFactKeyFor(taskType: VisionTaskType): String {
        return when (taskType) {
            VisionTaskType.KIT_DETECTION -> "kit_item_detected"
            VisionTaskType.STEP_VERIFICATION -> "step_visible_object"
            VisionTaskType.OBJECT_PRESENCE_CHECK -> "detected_object"
            VisionTaskType.INJURY_OBSERVATION -> "injury_observation"
            VisionTaskType.GENERAL_MULTIMODAL_QA -> "general_visual_fact"
            VisionTaskType.UNKNOWN -> "unknown_visual_fact"
        }
    }

    private fun isVisionTaskSupported(taskType: VisionTaskType): Boolean {
        return when (taskType) {
            VisionTaskType.KIT_DETECTION,
            VisionTaskType.STEP_VERIFICATION,
            VisionTaskType.OBJECT_PRESENCE_CHECK -> true
            VisionTaskType.INJURY_OBSERVATION,
            VisionTaskType.GENERAL_MULTIMODAL_QA,
            VisionTaskType.UNKNOWN -> false
        }
    }

    private fun visionTaskLabel(taskType: VisionTaskType): String {
        return when (taskType) {
            VisionTaskType.KIT_DETECTION -> "Kit inventory detection"
            VisionTaskType.STEP_VERIFICATION -> "Simple step verification"
            VisionTaskType.OBJECT_PRESENCE_CHECK -> "Object-presence check"
            VisionTaskType.INJURY_OBSERVATION -> "Injury observation"
            VisionTaskType.GENERAL_MULTIMODAL_QA -> "General multimodal QA"
            VisionTaskType.UNKNOWN -> "Unsupported vision task"
        }
    }

    private fun publishAiState(
        statusText: String?,
        progress: Float?,
        isPreparing: Boolean,
        isReady: Boolean,
        canRetry: Boolean,
        routeText: String?,
        diagnosticDetail: String?,
    ) {
        val cacheSummary = melangeModelManager?.inspectCache()?.summaryText()
        _viewState.value = _viewState.value.copy(
            aiStatusText = statusText,
            aiProgress = progress,
            isAiPreparing = isPreparing,
            isAiReady = isReady,
            canRetryAiPreparation = canRetry,
            aiModelLabel = melangeModelManager?.configuredModelLabel() ?: _viewState.value.aiModelLabel,
            aiRouteText = routeText,
            aiCacheSummaryText = cacheSummary,
            aiDiagnosticDetail = diagnosticDetail,
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
            val stepWarningText = buildWarningText(step)

            val currentState = currentDialogueState
            if (currentState is DialogueState.ProtocolMode && protocolPersonalizationKey(currentState) == cacheKey) {
                _viewState.value = _viewState.value.copy(
                    uiState = _viewState.value.uiState.copy(
                        title = protocol.title,
                        primaryInstruction = response.spokenText,
                        secondaryInstruction = null,
                        guidanceOriginLabel = if (response.usedFallback) {
                            "Canonical step retained after AI validation"
                        } else {
                            "AI-personalized step wording"
                        },
                        warningText = stepWarningText,
                        visualAids = visualAssetRepository.getAssetsForStep(protocol.protocolId, step.stepId),
                        currentStep = dialogueState.stepIndex + 1,
                        totalSteps = protocol.steps.size,
                        isAiAnswerPending = false,
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
                        title = "",
                        primaryInstruction = answerResult.answerText,
                        secondaryInstruction = "Continue with the current step.",
                        guidanceOriginLabel = null,
                        warningText = null,
                        visualAids = emptyList(),
                        currentStep = 0,
                        totalSteps = 0,
                        isAiAnswerPending = false,
                    ),
                    statusText = answerResult.fallbackReason ?: "Returning to the current step.",
                )
            }
        }
    }

    private fun protocolGuidanceOriginLabel(hasPersonalizedInstruction: Boolean): String {
        return when {
            !aiEnabled -> "Deterministic canonical step"
            hasPersonalizedInstruction -> "AI-personalized step wording"
            _viewState.value.isAiReady -> "Canonical step while AI rewrite is pending"
            else -> "Deterministic canonical step"
        }
    }

    private fun statusToRouteText(
        status: ModelLoadingStatus,
        treatFailedAsTransient: Boolean = false,
    ): String {
        return when (status) {
            ModelLoadingStatus.UNKNOWN -> "Startup cache and model check"
            ModelLoadingStatus.PENDING -> "Model constructor and metadata setup"
            ModelLoadingStatus.DOWNLOADING -> "Model payload download in progress"
            ModelLoadingStatus.TRANSFERRING -> "Model payload transfer and finalization"
            ModelLoadingStatus.COMPLETED -> _viewState.value.aiRouteText ?: "Local model ready"
            ModelLoadingStatus.FAILED -> if (treatFailedAsTransient) {
                "Local accelerator/backend probing"
            } else {
                _viewState.value.aiRouteText ?: "Model preparation failed"
            }
            ModelLoadingStatus.CANCELED -> "Model preparation canceled"
            ModelLoadingStatus.WAITING_FOR_WIFI -> "Waiting for network policy clearance"
            ModelLoadingStatus.NOT_INSTALLED -> "Play Asset Delivery lookup"
            ModelLoadingStatus.REQUIRES_USER_CONFIRMATION -> "Play Asset Delivery requires user confirmation"
        }
    }

    private fun classifyFailureRouteText(throwable: Throwable): String {
        val messageChain = generateSequence(throwable) { it.cause }
            .joinToString(" ") { cause -> cause.message.orEmpty() }
            .lowercase()

        return when {
            "bandwidth limit exceeded" in messageChain || "code is 429" in messageChain -> "Direct Melange model download"
            "itemstore" in messageChain || "assetpack" in messageChain || "onerror(-100)" in messageChain -> "Play Asset Delivery lookup"
            "unsatisfiedlinkerror" in messageChain || "libggml-cpu.so" in messageChain -> "Local native runtime load"
            else -> _viewState.value.aiRouteText ?: "Unknown preparation route"
        }
    }

    private fun protocolPersonalizationKey(dialogueState: DialogueState.ProtocolMode): String {
        val slotsHash = dialogueState.slots.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }
        return "${dialogueState.scenarioId}|${dialogueState.protocolId}|${dialogueState.stepIndex}|$slotsHash"
    }

    private fun questionAnswerKey(dialogueState: DialogueState.QuestionMode): String {
        return "${dialogueState.scenarioId}|${dialogueState.protocolId}|${dialogueState.stepIndex}|${dialogueState.userQuestion}"
    }

    private fun cancelResponseJobs() {
        personalizationJob?.cancel()
        questionAnswerJob?.cancel()
        personalizationJob = null
        questionAnswerJob = null
    }

    private data class SessionArchiveSnapshot(
        val sessionId: String,
        val title: String,
        val category: String?,
        val protocolId: String?,
        val createdAtMs: Long,
        val updatedAtMs: Long,
        val lastStepIndex: Int?,
        val totalSteps: Int?,
        val messages: List<SessionArchiveMessage>,
    )

    private suspend fun SessionRepository.archiveSessionSnapshot(snapshot: SessionArchiveSnapshot) {
        archiveSession(
            sessionId = snapshot.sessionId,
            title = snapshot.title,
            category = snapshot.category,
            protocolId = snapshot.protocolId,
            createdAtMs = snapshot.createdAtMs,
            updatedAtMs = snapshot.updatedAtMs,
            lastStepIndex = snapshot.lastStepIndex,
            totalSteps = snapshot.totalSteps,
            messages = snapshot.messages,
        )
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
        aiPreparationJob?.cancel()
        aiPreparationJob = null
        visionPreparationJob?.cancel()
        visionPreparationJob = null
        cancelResponseJobs()
        visionDetectionJob?.cancel()
        visionDetectionJob = null
        super.onCleared()
    }
}

class EmergencyViewModelFactory(
    private val dialogueStateManager: DialogueStateManager,
    private val protocolRepository: ProtocolRepository,
    private val visualAssetRepository: VisualAssetRepository,
    private val inferenceOrchestrator: InferenceOrchestrator,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val sessionRepository: SessionRepository? = null,
    private val melangeModelManager: MelangeModelManager? = null,
    private val melangeVisionModelManager: MelangeVisionModelManager? = null,
    private val visionDetectionEngine: VisionDetectionEngine? = null,
    private val embeddingPreparationStateHolder: EmbeddingPreparationStateHolder? = null,
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
                sessionRepository = sessionRepository,
                melangeModelManager = melangeModelManager,
                melangeVisionModelManager = melangeVisionModelManager,
                visionDetectionEngine = visionDetectionEngine,
                embeddingPreparationStateHolder = embeddingPreparationStateHolder,
                aiEnabled = aiEnabled,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}

private const val TAG = "EmergencyViewModel"
