package com.beeftechlabs.model.core

import kotlinx.serialization.Serializable

@Serializable
data class NodeHeartbeatResponse(
    val data: NodeHeartbeats
)

@Serializable
data class NodeHeartbeats(
    val heartbeats: List<NodeHeartbeat>
)

@Serializable
data class NodeHeartbeat(
    val publicKey: String,
    val identity: String,
    val nodeDisplayName: String,
    val peerType: String
)
