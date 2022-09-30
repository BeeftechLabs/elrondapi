package com.beeftechlabs.model.core

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val identity: String,
    val name: String,
    val blsKey: String,
    val type: NodeType,
    val eligible: Boolean,
    val provider: String? = null,
    val owner: String = "",
    val stake: String = "",
    val topUp: String = "",
    val locked: String = "",
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
