package com.beeftechlabs.repository

import com.beeftechlabs.cache.getFromStore
import com.beeftechlabs.cache.getFromStoreStrictly
import com.beeftechlabs.cache.peekStore
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.core.Node
import com.beeftechlabs.model.core.NodeHeartbeatResponse
import com.beeftechlabs.model.core.NodeType
import com.beeftechlabs.model.core.Nodes
import com.beeftechlabs.service.GatewayService
import com.beeftechlabs.util.fromBase64ToHexString
import kotlinx.coroutines.*

object NodeRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getAllNodes(): Nodes {
        return getFromStoreStrictly<Nodes>() ?: Nodes(withContext(Dispatchers.IO) {
            val cachedNodes = peekStore<Nodes>()?.value ?: emptyList()
            val cachedBlsKeys = cachedNodes.map { it.blsKey }
            val cachedBlsOwners = cachedNodes.map { it.blsKey to it.provider }.toMap()
            val heartbeats: Deferred<NodeHeartbeatResponse> = async { GatewayService.get("node/heartbeatstatus") }
            val providersDeferred = async { getFromStore<StakingProviders>().value }
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
            val providerOwners = providersDeferred.await().map { it.address to it.owner }.toMap()

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
        })
    }

    private suspend fun getBlsOwner(bls: String): Pair<String, String>? =
        SCRepository.vmQuery(
            elrondConfig.staking,
            "getOwner",
            listOf(bls),
            elrondConfig.auction
        ).firstOrNull()?.let { owner ->
            bls to Address(owner.fromBase64ToHexString()).erd
        }

    private const val NUM_PARALLEL_OWNER_FETCH = 200
}