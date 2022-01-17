package com.beeftechlabs.repository.token

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.token.Token
import com.beeftechlabs.model.token.TokenProperties
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.token.model.FungibleTokenResponse
import com.beeftechlabs.repository.token.model.GetEsdtsResponse
import com.beeftechlabs.service.GatewayService
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromBase64ToHexString
import com.beeftechlabs.util.toHexString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

object TokenRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getAllTokens(): AllTokens =
        coroutineScope {
            val fungibles = GatewayService.get<FungibleTokenResponse>("network/esdt/fungible-tokens").data.tokens

            val tokenProperties = fungibles.chunked(NUM_PARALLEL_FETCH)
                .map { chunk -> chunk.map { async { getTokenProperties(it) } }.awaitAll() }
                .flatten()

            // todo get assets

            AllTokens(tokenProperties)
        }

    suspend fun getTokensForAddress(address: String): List<Token> = coroutineScope {
        val esdtsDeferred = async { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values }
        val tokensDeferred = async { getAllTokens() }

        val esdts = esdtsDeferred.await()
        val tokens = tokensDeferred.await().value.associateBy { it.identifier }

        esdts.mapNotNull { esdt ->
            tokens[esdt.tokenIdentifier]?.let { props ->
                Token(
                    value = Value.extract(esdt.balance, esdt.tokenIdentifier.split("-").first()),
                    properties = props
                )
            }
        }
    }

    private suspend fun getTokenProperties(id: String): TokenProperties {
        val response = GatewayService.vmQuery(
            ScQueryRequest(elrondConfig.esdt, "getTokenProperties", listOf(id.toHexString()))
        )

        val properties = response.data.data.returnData?.mapIndexed { index, data ->
            if (index != 2) {
                data?.fromBase64String()?.let {
                    if (it.contains("-")) {
                        it.split("-").getOrNull(1)
                    } else {
                        it
                    }
                }
            } else {
                data?.fromBase64ToHexString()
            }
        }
        val type = properties?.getOrNull(1) ?: ""
        val isFungibleEsdt = type == "FungibleESDT"

        return TokenProperties(
            identifier = id,
            name = properties?.getOrNull(0) ?: "",
            type = type,
            owner = properties?.getOrNull(2)?.let { Address(it).erd } ?: "",
            minted = properties?.getOrNull(3) ?: "",
            burnt = properties?.getOrNull(4) ?: "",
            decimals = properties?.getOrNull(5)?.toIntOrNull() ?: 0,
            isPaused = properties?.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
            canUpgrade = properties?.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
            canMint = properties?.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
            canBurn = properties?.getOrNull(9)?.toBooleanStrictOrNull() ?: false,
            canChangeOwner = properties?.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
            canPause = properties?.getOrNull(11)?.toBooleanStrictOrNull() ?: false,
            canFreeze = properties?.getOrNull(12)?.toBooleanStrictOrNull() ?: false,
            canWipe = properties?.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
            canAddSpecialRoles = !isFungibleEsdt && properties?.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
            canTransferNFTCreateRole = !isFungibleEsdt && properties?.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
            nftCreateStopped = !isFungibleEsdt && properties?.getOrNull(16)?.toBooleanStrictOrNull() ?: false,
            wiped = if (isFungibleEsdt) null else properties?.getOrNull(17) ?: ""
        )
    }

    private const val NUM_PARALLEL_FETCH = 100
}

@Serializable
data class AllTokens(
    val value: List<TokenProperties>
) {
    companion object {
        suspend fun cached() = withCache(CacheType.Tokens) { TokenRepository.getAllTokens() }
    }
}
