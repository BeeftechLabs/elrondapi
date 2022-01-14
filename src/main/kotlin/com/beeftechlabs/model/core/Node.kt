package com.beeftechlabs.model.core

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val identity: String,
    val name: String,
    val blsKey: String,
    val type: NodeType,
    val eligible: Boolean,
    val provider: String = "",
    val owner: String = ""
)

enum class NodeType {
    Validator, Observer;

    companion object {
        fun fromString(type: String) = when (type) {
            "waiting", "eligible" -> Validator
            else -> Observer
        }
    }
}

data class Nodes(
    val value: List<Node>
)