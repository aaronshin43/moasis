package com.example.moasis.domain.state

import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.nlu.NluRouter
import com.example.moasis.domain.nlu.SlotExtractor

class DialogueStateManager(
    private val protocolRepository: ProtocolRepository,
    private val nluRouter: NluRouter = NluRouter(),
    private val entryTreeRouter: EntryTreeRouter = EntryTreeRouter(),
    private val protocolStateMachine: ProtocolStateMachine = ProtocolStateMachine(),
    private val interruptionRouter: InterruptionRouter = InterruptionRouter(),
    private val slotExtractor: SlotExtractor = SlotExtractor(),
) {
    fun handleText(
        text: String,
        currentState: DialogueState? = null,
    ): DialogueTurnResult {
        return when (currentState) {
            null,
            DialogueState.Completed -> handleNewInput(text)

            is DialogueState.ProtocolMode -> handleProtocolMode(text, currentState)

            is DialogueState.EntryMode -> handleEntryMode(text, currentState)

            is DialogueState.QuestionMode -> {
                DialogueTurnResult(
                    dialogueState = DialogueState.ProtocolMode(
                        scenarioId = currentState.scenarioId,
                        protocolId = currentState.protocolId,
                        stepIndex = currentState.returnToStepIndex,
                        suspendedByQuestion = false,
                        isSpeaking = false,
                    ),
                )
            }

            is DialogueState.ReTriageMode -> handleNewInput(text)
        }
    }

    fun handleTurn(
        turn: UserTurn,
        currentState: DialogueState? = null,
    ): DialogueTurnResult {
        val text = turn.text ?: turn.voiceTranscript.orEmpty()
        return handleText(text, currentState)
    }

    private fun handleNewInput(text: String): DialogueTurnResult {
        val nluResult = nluRouter.route(text)
        val treeId = entryTreeRouter.route(nluResult)
        val tree = requireNotNull(protocolRepository.getTree(treeId)) {
            "Missing tree asset for $treeId"
        }

        val evaluation = protocolStateMachine.evaluateTree(
            tree = tree,
            slots = nluResult.slots,
            indicators = nluResult.domainHints.map { it.name.lowercase() }.toSet() + nluResult.slots.keys,
        )

        return evaluation.toDialogueResult(
            treeId = treeId,
            scenarioId = nluResult.domainHints.firstOrNull()?.name?.lowercase() ?: treeId,
            entryIntent = nluResult.entryIntent,
            domainHints = nluResult.domainHints.map { it.name }.toSet(),
        )
    }

    private fun handleEntryMode(
        text: String,
        currentState: DialogueState.EntryMode,
    ): DialogueTurnResult {
        val mergedSlots = currentState.slots + slotExtractor.extract(text).let { extracted ->
            when {
                extracted["response"] == "yes" -> mapOf(currentState.nodeIdToSlotKey() to "yes")
                extracted["response"] == "no" -> mapOf(currentState.nodeIdToSlotKey() to "no")
                else -> extracted
            }
        }.filterKeys { it.isNotBlank() }

        val tree = requireNotNull(protocolRepository.getTree(currentState.treeId)) {
            "Missing tree asset for ${currentState.treeId}"
        }

        val evaluation = protocolStateMachine.evaluateTree(tree, mergedSlots, indicators = mergedSlots.keys)
        return evaluation.toDialogueResult(
            treeId = currentState.treeId,
            scenarioId = currentState.treeId,
            entryIntent = null,
            domainHints = emptySet(),
        )
    }

    private fun handleProtocolMode(
        text: String,
        currentState: DialogueState.ProtocolMode,
    ): DialogueTurnResult {
        val interruptionDecision = interruptionRouter.classify(text)
        return when (interruptionDecision.type) {
            InterruptionType.STATE_CHANGING_REPORT -> DialogueTurnResult(
                dialogueState = DialogueState.ReTriageMode(
                    previousScenarioId = currentState.scenarioId,
                    newInput = text,
                ),
                interruptionDecision = interruptionDecision,
            )

            InterruptionType.CLARIFICATION_QUESTION -> DialogueTurnResult(
                dialogueState = DialogueState.QuestionMode(
                    scenarioId = currentState.scenarioId,
                    protocolId = currentState.protocolId,
                    stepIndex = currentState.stepIndex,
                    userQuestion = text,
                    returnToStepIndex = currentState.stepIndex,
                ),
                protocolId = currentState.protocolId,
                stepId = protocolRepository.getProtocol(currentState.protocolId)
                    ?.let { protocolStateMachine.currentStepId(it, currentState.stepIndex) },
                interruptionDecision = interruptionDecision,
            )

            InterruptionType.CONTROL_INTENT -> {
                val protocol = requireNotNull(protocolRepository.getProtocol(currentState.protocolId)) {
                    "Missing protocol asset for ${currentState.protocolId}"
                }
                val nextState = protocolStateMachine.advanceProtocol(
                    protocol = protocol,
                    currentState = currentState,
                    controlIntent = interruptionDecision.controlIntent ?: ControlIntent.UNKNOWN,
                )
                DialogueTurnResult(
                    dialogueState = nextState,
                    protocolId = if (nextState is DialogueState.ProtocolMode) nextState.protocolId else currentState.protocolId,
                    stepId = if (nextState is DialogueState.ProtocolMode) {
                        protocolStateMachine.currentStepId(protocol, nextState.stepIndex)
                    } else {
                        null
                    },
                    interruptionDecision = interruptionDecision,
                )
            }

            InterruptionType.OUT_OF_DOMAIN -> DialogueTurnResult(
                dialogueState = currentState,
                protocolId = currentState.protocolId,
                stepId = protocolRepository.getProtocol(currentState.protocolId)
                    ?.let { protocolStateMachine.currentStepId(it, currentState.stepIndex) },
                interruptionDecision = interruptionDecision,
            )
        }
    }

    private fun TreeEvaluation.toDialogueResult(
        treeId: String,
        scenarioId: String,
        entryIntent: com.example.moasis.domain.model.EntryIntent?,
        domainHints: Set<String>,
    ): DialogueTurnResult {
        return when (this) {
            is TreeEvaluation.AwaitingNode -> DialogueTurnResult(
                dialogueState = DialogueState.EntryMode(
                    treeId = treeId,
                    nodeId = nodeId,
                    slots = slots,
                    history = history,
                ),
                currentNodeId = nodeId,
                entryIntent = entryIntent?.name,
                domainHints = domainHints,
            )

            is TreeEvaluation.ProtocolSelected -> {
                val protocol = requireNotNull(protocolRepository.getProtocol(protocolId)) {
                    "Missing protocol asset for $protocolId"
                }
                DialogueTurnResult(
                    dialogueState = DialogueState.ProtocolMode(
                        scenarioId = scenarioId,
                        protocolId = protocolId,
                        stepIndex = 0,
                        slots = slots,
                        isSpeaking = false,
                    ),
                    protocolId = protocolId,
                    stepId = protocolStateMachine.currentStepId(protocol, 0),
                    entryIntent = entryIntent?.name,
                    domainHints = domainHints,
                )
            }

            is TreeEvaluation.TreeRedirect -> {
                val nextTree = requireNotNull(protocolRepository.getTree(this.treeId)) {
                    "Missing tree asset for ${this.treeId}"
                }
                protocolStateMachine.evaluateTree(nextTree, slots, indicators = slots.keys)
                    .toDialogueResult(
                        treeId = this.treeId,
                        scenarioId = scenarioId,
                        entryIntent = entryIntent,
                        domainHints = domainHints,
                    )
            }

            is TreeEvaluation.Terminal -> DialogueTurnResult(
                dialogueState = DialogueState.Completed,
                entryIntent = entryIntent?.name,
                domainHints = domainHints,
            )
        }
    }

    private fun DialogueState.EntryMode.nodeIdToSlotKey(): String {
        return when (nodeId) {
            "scene_safe" -> "scene_safe"
            "responsive_check" -> "responsive"
            "breathing_check" -> "breathing_normal"
            "burn_severity" -> "burn_severity"
            else -> ""
        }
    }
}

data class DialogueTurnResult(
    val dialogueState: DialogueState,
    val currentNodeId: String? = null,
    val protocolId: String? = null,
    val stepId: String? = null,
    val entryIntent: String? = null,
    val domainHints: Set<String> = emptySet(),
    val interruptionDecision: InterruptionDecision? = null,
)
