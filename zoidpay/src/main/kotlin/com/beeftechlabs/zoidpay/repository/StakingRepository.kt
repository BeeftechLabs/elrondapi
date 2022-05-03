package com.beeftechlabs.zoidpay.repository

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.putInCache
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.component6
import com.beeftechlabs.zoidpay.model.ClaimableReward
import com.beeftechlabs.zoidpay.model.Pool
import com.beeftechlabs.zoidpay.model.Stake
import kotlinx.serialization.Serializable

object StakingRepository {

    private val zoidPayConfig by lazy { config.zoidpay!! }

    suspend fun getDelegators(poolAddress: String): List<String> {
        startCustomTrace("zoidpay:getDelegators:$poolAddress")

        val scQuery = SCService.vmQueryParsed(
            ScQueryRequest(
                scAddress = zoidPayConfig.stakingSC,
                funcName = "getDelegators",
                args = listOf(Address(poolAddress).hex)
            )
        )

        return scQuery.output.mapNotNull { result -> result.hex?.let { Address(it).erd } }.also {
            endCustomTrace("zoidpay:getDelegators:$poolAddress")
        }
    }

    suspend fun getStakes(poolAddress: String, start: Int, size: Int): List<Stake> {
        val (delegators, _) = Delegators.forPool(poolAddress)
        return getStakes(poolAddress, delegators.drop(start).take(size))
    }

    private suspend fun getStakes(poolAddress: String, delegators: List<String>): List<Stake> {
        startCustomTrace("zoidpay:getDelegatorsDetails:$poolAddress")

        val scQuery = SCService.vmQueryParsed(ScQueryRequest(
            scAddress = zoidPayConfig.stakingSC,
            funcName = "getStakesForPoolDelegators",
            args = listOf(Address(poolAddress).hex) + delegators.map { Address(it).hex }
        ))

        return scQuery.output.chunked(5).map { (address, stakedAmount, timestamp, months, isPool) ->
            Stake(
                address.hex?.let { Address(it).erd } ?: "",
                stakedAmount.bigNumber?.let { Value.extract(it, zoidPayConfig.tokenId) }
                    ?: Value.zero(zoidPayConfig.tokenId),
                timestamp.long ?: 0,
                months.long?.toInt() ?: 0,
                isPool.boolean ?: false
            )
        }.also {
            endCustomTrace("zoidpay:getDelegatorsDetails:$poolAddress")
        }
    }

    suspend fun getPools(): List<Pool> {
        startCustomTrace("zoidpay:getPools")

        val scQuery = SCService.vmQueryParsed(
            ScQueryRequest(
                scAddress = zoidPayConfig.stakingSC,
                funcName = "getPools"
            )
        )

        return scQuery.output.chunked(6).map { (id, owner, totalStaked, zoidsters, name, status) ->
            Pool(
                id.hex?.let { Address(it).erd } ?: "",
                owner.hex?.let { Address(it).erd } ?: "",
                totalStaked.bigNumber?.let { Value.extract(it, zoidPayConfig.tokenId) }
                    ?: Value.zero(zoidPayConfig.tokenId),
                zoidsters.long ?: 0,
                name.string ?: "",
                status.long?.toInt() ?: 0
            )
        }.also {
            endCustomTrace("zoidpay:getPools")
        }
    }

    suspend fun getStakesForDelegatorNew(address: String): List<Stake> =
        withCache(CacheType.ZoidPayAddressStake, instance = address) {
            startCustomTrace("zoidpay:getStakesForDelegator:$address")

            val scQuery = SCService.vmQueryParsed(
                ScQueryRequest(
                    scAddress = zoidPayConfig.stakingSC,
                    funcName = "getStakesForDelegator",
                    args = listOf(Address(address).hex)
                )
            )

            scQuery.output.chunked(5).map { (address, stakedAmount, timestamp, months, isPool) ->
                Stake(
                    address.hex?.let { Address(it).erd } ?: "",
                    stakedAmount.bigNumber?.let { Value.extract(it, zoidPayConfig.tokenId) }
                        ?: Value.zero(zoidPayConfig.tokenId),
                    timestamp.long ?: 0,
                    months.long?.toInt() ?: 0,
                    isPool.boolean ?: false
                )
            }.also {
                endCustomTrace("zoidpay:getStakesForDelegator:$address")
            }
        }

    suspend fun getStakesForDelegator(address: String): List<Stake> =
        withCache(CacheType.ZoidPayAddressStake, instance = address) {
            startCustomTrace("zoidpay:getStakesForDelegator:$address")

            val scQuery = SCService.vmQueryParsed(
                ScQueryRequest(
                    scAddress = zoidPayConfig.stakingSC,
                    funcName = "getStaking",
                    args = listOf(Address(address).hex)
                )
            )

            scQuery.output.mapNotNull { scResult ->
                scResult.hex?.let { hex ->
                    var data = hex
                    val poolAddressHex = data.take(64)
                    data = data.drop(64)
                    val amtSize = data.take(8).toInt(16) * 2
                    data = data.drop(8)
                    val stakeAmt = data.take(amtSize)
                    data = data.drop(amtSize)
                    val timestamp = data.take(16).toLong(16)
                    data = data.drop(16)
                    val months = data.take(4).toInt(16)
                    data = data.drop(4)
                    val isPool = data.take(2).toInt(16) > 0
                    Stake(
                        Address(poolAddressHex).erd,
                        Value.extractHex(stakeAmt, zoidPayConfig.tokenId),
                        timestamp,
                        months,
                        isPool
                    )
                }
            }.also {
                endCustomTrace("zoidpay:getStakesForDelegator:$address")
            }
        }

    suspend fun getClaimableRewards(address: String): Value =
        withCache(CacheType.ZoidPayAddressClaimable, instance = address) {
            startCustomTrace("zoidpay:getClaimableRewards:$address")

            val scQuery = SCService.vmQueryParsed(
                ScQueryRequest(
                    scAddress = zoidPayConfig.stakingSC,
                    funcName = "claimableRewards",
                    args = listOf(Address(address).hex)
                )
            )

            scQuery.output.firstOrNull()?.bigNumber?.let { Value.extract(it, zoidPayConfig.tokenId) }
                ?: Value.zero(
                    zoidPayConfig.tokenId
                ).also {
                    endCustomTrace("zoidpay:getClaimableRewards:$address")
                }
        }

    suspend fun getClaimableRewardsPerStake(address: String): List<ClaimableReward> =
        withCache(CacheType.ZoidPayAddressClaimablePerStake, instance = address) {
            startCustomTrace("zoidpay:getStakesForDelegator:$address")

            val scQuery = SCService.vmQueryParsed(
                ScQueryRequest(
                    scAddress = zoidPayConfig.stakingSC,
                    funcName = "getStakeRewards",
                    args = listOf(Address(address).hex)
                )
            )

            scQuery.output.chunked(3).map { (pool, timestamp, reward) ->
                ClaimableReward(
                    pool.hex?.let { Address(it).erd } ?: "",
                    timestamp.long ?: 0,
                    reward.bigNumber?.let { Value.extract(it, zoidPayConfig.tokenId) }
                        ?: Value.zero(zoidPayConfig.tokenId)
                )
            }.also {
                endCustomTrace("zoidpay:getStakesForDelegator:$address")
            }
        }
}

@Serializable
class Delegators {
    companion object {
        suspend fun forPool(poolAddress: String, skipCache: Boolean = false): Pair<List<String>, Boolean> {
            var fromCache = !skipCache
            val delegators = if (skipCache) {
                StakingRepository.getDelegators(poolAddress)
                    .also { putInCache(CacheType.ZoidPayDelegators, it, poolAddress) }
            } else {
                withCache(CacheType.ZoidPayDelegators, poolAddress) {
                    StakingRepository.getDelegators(poolAddress).also { fromCache = false }
                }
            }
            return delegators to fromCache
        }
    }
}

//@Serializable
//data class DelegatorsDetails(
//    val stakes: List<Stake>,
//    val start: Int,
//    val end: Int
//) {
//    fun contains(start: Int, size: Int) = this.start <= start && end >= start + size
//
//    fun slice(start: Int, size: Int) = DelegatorsDetails(
//        stakes.drop(start - this.start).take(size),
//        start,
//        start + size
//    )
//
//    operator fun plus(other: DelegatorsDetails) = DelegatorsDetails(
//        stakes + other.stakes,
//        minOf(start, other.start),
//        maxOf(end, other.end)
//    )
//
//    companion object {
//        suspend fun forPool(poolAddress: String, start: Int, size: Int, skipCache: Boolean = false): List<Stake> {
//            val (delegators, fromCache) = Delegators.forPool(poolAddress, skipCache)
//            val currentPage = delegators.drop(start).take(size)
//
//            val details = if (skipCache || !fromCache) {
//                DelegatorsDetails(
//                    StakingRepository.getStakes(poolAddress, currentPage),
//                    start,
//                    start + size
//                ).also { putInCache(
//                    CacheType.ZoidPayDelegatorsDetails,
//                    it,
//                    poolAddress
//                ) }
//            } else {
//                val existing = withCache(CacheType.ZoidPayDelegatorsDetails) {
//                    DelegatorsDetails(StakingRepository.getStakes(poolAddress, currentPage), start, start + size)
//                }
//                if (existing.contains(start, size)) {
//                    existing.slice(start, size)
//                } else {
//                    if (start + size > existing.end) {
//                        val required = delegators.drop(existing.end).take(start + size - existing.end)
//                        DelegatorsDetails(existing.stakes + StakingRepository.getStakes(poolAddress, required), existing.start, start + size)
//                    } else {
//                        val required = delegators.drop(start).take(existing.start - start)
//                        DelegatorsDetails(StakingRepository.getStakes(poolAddress, required) + existing.stakes, start, existing.end)
//                    }
//                }
//            }
//
//            return details.delegators
//        }
//    }
//}