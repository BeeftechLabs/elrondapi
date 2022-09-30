package com.beeftechlabs.repository.node

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.peekCache
import com.beeftechlabs.cache.putInCache
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.core.Node
import com.beeftechlabs.model.core.NodeHeartbeatResponse
import com.beeftechlabs.model.core.NodeType
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.token.toValue
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.repository.StakingProviders
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
        val providersDeferred = async { StakingProviders.all().value }
        val nodes = heartbeats.await().data.heartbeats.map {
            with(it) {
                Node(
                    identity = identity,
                    name = nodeDisplayName,
                    blsKey = publicKey,
                    type = NodeType.fromString(peerType),
                    eligible = peerType == "eligible",
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
                (missingBlsOwners[it.blsKey] ?: cachedBlsOwners[it.blsKey])?.let { address ->
                    providerOwners[address]?.let { providerOwner ->
                        it.copy(
                            provider = address,
                            owner = providerOwner
                        )
                    } ?: it.copy(owner = address)
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

    suspend fun getStakesForAddress(address: String): OwnerStake = withContext(Dispatchers.IO) {
        withCache(CacheType.OwnerStakes, address) {

            val providersDeferred = async { StakingProviders.all().value }

            val values = SCService.vmQueryParsed(
                ScQueryRequest(
                    elrondConfig.auction,
                    "getTotalStakedTopUpStakedBlsKeys",
                    listOf(Address(address).hex),
                    elrondConfig.auction
                )
            ).output

            val providers = providersDeferred.await()
            val provider = providers.find { it.address == address }

            val topUp = Value.extract(values[0].bigNumber, Value.EGLD)
            val numNodes = values[2].long?.toInt() ?: 1

            OwnerStake(
                address = address,
                name = provider?.metadata?.name,
                topUp = topUp,
                topUpPerNode = topUp.bigValue().div(numNodes).toValue(Value.EGLD),
                staked = Value.extract(values[1].bigNumber, Value.EGLD),
                numNodes = numNodes,
                blsKeys = values.drop(3).mapNotNull { it.hex },
            )
        }
    }

    suspend fun getAllStakes(): List<OwnerStake> = withContext(Dispatchers.IO) {
        withCache(CacheType.AllStakes) {
            val nodes = getAllNodes().value
            val owners = nodes.map { node -> node.provider.takeIf { !it.isNullOrEmpty() } ?: node.owner }.distinct()

            owners.chunked(NUM_PARALLEL_OWNER_FETCH).map { chunks ->
                chunks.map { async { getStakesForAddress(it) } }.awaitAll()
            }.flatten()
        }
    }

    private const val NUM_PARALLEL_OWNER_FETCH = 100
}

@Serializable
data class Nodes(
    val value: List<Node>
) {
    companion object {
        suspend fun all(skipCache: Boolean = false) = if (skipCache) {
            NodeRepository.getAllNodes().also { putInCache(CacheType.Nodes, it) }
        } else {
            withCache(CacheType.Nodes) { NodeRepository.getAllNodes() }
        }
    }
}
