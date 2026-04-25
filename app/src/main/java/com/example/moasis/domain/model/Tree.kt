package com.example.moasis.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tree(
    @SerialName("tree_id")
    val treeId: String,
    val version: String,
    @SerialName("start_node")
    val startNode: String,
    val nodes: List<TreeNode> = emptyList(),
)

@Serializable
data class TreeNode(
    val id: String,
    val type: String,
    val prompt: String? = null,
    @SerialName("instruction_id")
    val instructionId: String? = null,
    @SerialName("slot_key")
    val slotKey: String? = null,
    val transitions: List<Transition> = emptyList(),
    val routes: List<Route> = emptyList(),
    val next: String? = null,
    @SerialName("fallback_to")
    val fallbackTo: String? = null,
    @SerialName("safety_flags")
    val safetyFlags: List<String> = emptyList(),
)

@Serializable
data class Transition(
    @SerialName("when")
    val condition: String,
    val to: String? = null,
    @SerialName("to_tree")
    val toTree: String? = null,
)

@Serializable
data class Route(
    @SerialName("if")
    val condition: String,
    val to: String? = null,
    @SerialName("to_tree")
    val toTree: String? = null,
)
