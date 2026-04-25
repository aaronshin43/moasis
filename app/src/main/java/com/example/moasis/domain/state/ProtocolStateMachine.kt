package com.example.moasis.domain.state

import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.Tree
import com.example.moasis.domain.model.TreeNode

class ProtocolStateMachine {
    fun evaluateTree(
        tree: Tree,
        slots: Map<String, String>,
        indicators: Set<String> = emptySet(),
    ): TreeEvaluation {
        val nodesById = tree.nodes.associateBy { it.id }
        var currentNodeId = tree.startNode
        val history = mutableListOf<String>()
        val maxIterations = tree.nodes.size + 2
        var iterations = 0

        while (iterations < maxIterations) {
            iterations += 1
            val node = nodesById[currentNodeId]
                ?: return TreeEvaluation.AwaitingNode(
                    nodeId = currentNodeId,
                    prompt = null,
                    slots = slots,
                    history = history,
                )
            history += node.id

            when (node.type) {
                "question" -> {
                    val answer = node.slotKey?.let { resolveSlotAnswer(it, slots) }
                    val transition = node.transitions.firstOrNull { it.condition == answer }
                    if (transition != null) {
                        if (!transition.to.isNullOrBlank()) {
                            currentNodeId = transition.to
                            continue
                        }
                        if (!transition.toTree.isNullOrBlank()) {
                            return TreeEvaluation.TreeRedirect(
                                treeId = transition.toTree,
                                slots = slots,
                                history = history,
                            )
                        }
                        continue
                    }
                    return TreeEvaluation.AwaitingNode(
                        nodeId = node.id,
                        prompt = node.prompt,
                        slots = slots,
                        history = history,
                    )
                }

                "instruction" -> {
                    val instructionId = node.instructionId
                    if (!instructionId.isNullOrBlank() && looksLikeProtocolId(instructionId)) {
                        return TreeEvaluation.ProtocolSelected(
                            protocolId = instructionId,
                            slots = slots,
                            safetyFlags = node.safetyFlags,
                        )
                    }
                    val nextNodeId = node.next
                        ?: return TreeEvaluation.AwaitingNode(
                            nodeId = node.id,
                            prompt = node.prompt,
                            slots = slots,
                            history = history,
                        )
                    currentNodeId = nextNodeId
                }

                "router", "route" -> {
                    val route = node.routes.firstOrNull { routeMatches(it.condition, slots, indicators) }
                    if (route != null) {
                        if (!route.to.isNullOrBlank()) {
                            currentNodeId = route.to
                            continue
                        }
                        if (!route.toTree.isNullOrBlank()) {
                            return TreeEvaluation.TreeRedirect(
                                treeId = route.toTree,
                                slots = slots,
                                history = history,
                            )
                        }
                        continue
                    }
                    val fallback = node.fallbackTo
                    if (!fallback.isNullOrBlank() && fallback != node.id && nodesById.containsKey(fallback)) {
                        currentNodeId = fallback
                        continue
                    }
                    if (!fallback.isNullOrBlank() && fallback != node.id) {
                        return TreeEvaluation.TreeRedirect(
                            treeId = fallback,
                            slots = slots,
                            history = history,
                        )
                    }
                    return TreeEvaluation.AwaitingNode(
                        nodeId = node.id,
                        prompt = node.prompt,
                        slots = slots,
                        history = history,
                    )
                }

                "terminal" -> {
                    return TreeEvaluation.Terminal(
                        slots = slots,
                        history = history,
                    )
                }

                else -> {
                    return TreeEvaluation.AwaitingNode(
                        nodeId = node.id,
                        prompt = node.prompt,
                        slots = slots,
                        history = history,
                    )
                }
            }
        }

        return TreeEvaluation.AwaitingNode(
            nodeId = currentNodeId,
            prompt = null,
            slots = slots,
            history = history,
        )
    }

    fun advanceProtocol(
        protocol: Protocol,
        currentState: DialogueState.ProtocolMode,
        controlIntent: ControlIntent,
    ): DialogueState {
        return when (controlIntent) {
            ControlIntent.NEXT,
            ControlIntent.DONE -> {
                val nextIndex = currentState.stepIndex + 1
                if (nextIndex >= protocol.steps.size) {
                    DialogueState.Completed
                } else {
                    currentState.copy(stepIndex = nextIndex, suspendedByQuestion = false)
                }
            }

            ControlIntent.REPEAT,
            ControlIntent.STOP,
            ControlIntent.UNKNOWN -> currentState
        }
    }

    fun currentStepId(protocol: Protocol, stepIndex: Int): String? {
        return protocol.steps.getOrNull(stepIndex)?.stepId
    }

    private fun resolveSlotAnswer(slotKey: String, slots: Map<String, String>): String? {
        return slots[slotKey] ?: slots["response"]
    }

    private fun routeMatches(
        condition: String,
        slots: Map<String, String>,
        indicators: Set<String>,
    ): Boolean {
        if ("&" in condition) {
            return condition.split("&").all { part ->
                routeMatches(part.trim(), slots, indicators)
            }
        }
        if ("=" in condition) {
            val (key, expected) = condition.split("=", limit = 2)
            return slots[key.trim()] == expected.trim()
        }
        return condition in indicators || slots[condition] == "yes"
    }

    private fun looksLikeProtocolId(instructionId: String): Boolean {
        return instructionId.endsWith("_general")
    }
}

sealed class TreeEvaluation {
    data class AwaitingNode(
        val nodeId: String,
        val prompt: String?,
        val slots: Map<String, String>,
        val history: List<String>,
    ) : TreeEvaluation()

    data class ProtocolSelected(
        val protocolId: String,
        val slots: Map<String, String>,
        val safetyFlags: List<String> = emptyList(),
    ) : TreeEvaluation()

    data class TreeRedirect(
        val treeId: String,
        val slots: Map<String, String>,
        val history: List<String>,
    ) : TreeEvaluation()

    data class Terminal(
        val slots: Map<String, String>,
        val history: List<String>,
    ) : TreeEvaluation()
}
