package com.beeftechlabs.repository

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.peekCache
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.core.Node
import com.beeftechlabs.model.core.NodeHeartbeatResponse
import com.beeftechlabs.model.core.NodeType
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.service.GatewayService
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.fromBase64ToHexString
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

object NodeRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getAllNodes(): Nodes = withContext(Dispatchers.IO) {
        startCustomTrace("AllNodes")
        val cachedNodes = peekCache<Nodes>(CacheType.Nodes)?.value ?: emptyList()
        val cachedBlsKeys = cachedNodes.map { it.blsKey }
        val cachedBlsOwners = cachedNodes.associate { it.blsKey to it.provider }
        val heartbeats: Deferred<NodeHeartbeatResponse> = async { GatewayService.get("node/heartbeatstatus") }
        val providersDeferred = async { StakingProviders.cached().value }
        val nodes = heartbeats.await().data.heartbeats.map {
            with(it) {
                Node(
                    identity = identity,
                    name = nodeDisplayName,
                    blsKey = publicKey,
                    type = NodeType.fromString(peerType),
                    eligible = peerType == "eligible"
                )
            }
        }
        val missingBlsKeys = nodes.map { it.blsKey } - cachedBlsKeys.toSet()
        val missingBlsOwners = missingBlsKeys.chunked(NUM_PARALLEL_OWNER_FETCH).map { chunks ->
            chunks.map { async { getBlsOwner(it) } }.awaitAll()
        }.flatten().mapNotNull { it }.toMap()
        val providerOwners = providersDeferred.await().associate { it.address to it.owner }

        Nodes(
            nodes.mapNotNull {
                (missingBlsOwners[it.blsKey] ?: cachedBlsOwners[it.blsKey])?.let { provider ->
                    providerOwners[provider]?.let { owner ->
                        it.copy(
                            provider = provider,
                            owner = owner
                        )
                    }
                }
            }
        )
    }.also {
        endCustomTrace("AllNodes")
    }

    private suspend fun getBlsOwner(bls: String): Pair<String, String>? =
        SCService.vmQuery(
            elrondConfig.staking,
            "getOwner",
            listOf(bls),
            elrondConfig.auction
        ).firstOrNull()?.let { owner ->
            bls to Address(owner.fromBase64ToHexString()).erd
        }

    private const val NUM_PARALLEL_OWNER_FETCH = 100
}

@Serializable
data class Nodes(
    val value: List<Node>
) {
    companion object {
        suspend fun cached() = withCache(CacheType.Nodes) { NodeRepository.getAllNodes() }
    }
}
