package com.beeftechlabs.repository.token

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.putInCache
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.token.Token
import com.beeftechlabs.model.token.TokenProperties
import com.beeftechlabs.model.token.TokenType
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
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
import mu.KotlinLogging

object TokenRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getAllEsdts(): Esdts = coroutineScope {
        startCustomTrace("AllEsdts")
        val fungibles = GatewayService.get<FungibleTokenResponse>("network/esdt/fungible-tokens").data.tokens

        val assets = AllTokenAssets.get().value

        val tokenProperties = fungibles.chunked(NUM_PARALLEL_FETCH)
            .map { chunk -> chunk.map { async { getTokenProperties(it) } }.awaitAll() }
            .flatten()
            .map { it.copy(assets = assets[it.identifier]) }

        Esdts(tokenProperties)
    }.also {
        endCustomTrace("AllEsdts")
    }

    suspend fun getAllNfts(): Nfts = coroutineScope {
        startCustomTrace("AllNfts")
        val fungibles = GatewayService.get<FungibleTokenResponse>("network/esdt/non-fungible-tokens").data.tokens

        val assets = AllTokenAssets.get().value

        val tokenProperties = fungibles.chunked(NUM_PARALLEL_FETCH)
            .map { chunk -> chunk.map { async { getTokenProperties(it) } }.awaitAll() }
            .flatten()
            .map { it.copy(assets = assets[it.identifier]) }

        Nfts(tokenProperties)
    }.also {
        endCustomTrace("AllNfts")
    }

    suspend fun getAllSfts(): Sfts = coroutineScope {
        startCustomTrace("AllSfts")
        val fungibles = GatewayService.get<FungibleTokenResponse>("network/esdt/semi-fungible-tokens").data.tokens

        val assets = AllTokenAssets.get().value

        val tokenProperties = fungibles.chunked(NUM_PARALLEL_FETCH)
            .map { chunk -> chunk.map { async { getTokenProperties(it) } }.awaitAll() }
            .flatten()
            .map { it.copy(assets = assets[it.identifier]) }

        Sfts(tokenProperties)
    }.also {
        endCustomTrace("AllSfts")
    }

    suspend fun getEsdtsForAddress(address: String): List<Token> = coroutineScope {
        startCustomTrace("TokensForAddress:$address")
        val addressEsdtsDeferred =
            async { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values }
        val esdtsDeferred = async { Esdts.all() }

        val assets = AllTokenAssets.get().value

        val addressEsdts = addressEsdtsDeferred.await()
        val esdts = esdtsDeferred.await().value.associateBy { it.identifier }

        addressEsdts.mapNotNull { esdt ->
            val identifierParts = esdt.tokenIdentifier.split("-")
            val commonIdentifier = identifierParts.take(2).joinToString("-")
            (esdts[commonIdentifier]
                ?: getTokenProperties(commonIdentifier)
                    .takeIf { it.type == TokenType.ESDT || it.type == TokenType.MetaESDT }?.copy(
                        assets = assets[commonIdentifier]
                    ))?.let {
                Token(
                    value = Value.extract(esdt.balance, commonIdentifier) ?: Value.zero(identifierParts.first()),
                    properties = it
                )
            }
        }
    }.also {
        endCustomTrace("TokensForAddress:$address")
    }

    suspend fun getEsdtWithId(id: String): TokenProperties? =
        Esdts.all().value.firstOrNull { it.identifier == id }

    suspend fun getNftsForAddress(address: String): List<Token> = coroutineScope {
        startCustomTrace("NftsForAddress:$address")
        val esdtsDeferred =
            async { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values }
        val nftsDeferred = async { Nfts.all() }

        val esdts = esdtsDeferred.await()
        val nfts = nftsDeferred.await().value.associateBy { it.identifier }

        esdts.mapNotNull { esdt ->
            val identifierParts = esdt.tokenIdentifier.split("-")
            val commonIdentifier = identifierParts.take(2).joinToString("-")
            (nfts[commonIdentifier]
                ?: getTokenProperties(commonIdentifier)
                    .takeIf { it.type == TokenType.NFT })?.let {
                Token(
                    value = Value.extract(esdt.balance, commonIdentifier) ?: Value.zero(identifierParts.first()),
                    properties = it
                )
            }
        }
    }.also {
        endCustomTrace("NftsForAddress:$address")
    }

    suspend fun getNftWithId(id: String): TokenProperties? =
        Nfts.all().value.firstOrNull { it.identifier == id }

    suspend fun getSftsForAddress(address: String): List<Token> = coroutineScope {
        startCustomTrace("SftsForAddress:$address")
        val esdtsDeferred =
            async { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values }
        val sftsDeferred = async { Sfts.all() }

        val esdts = esdtsDeferred.await()
        val sfts = sftsDeferred.await().value.associateBy { it.identifier }

        esdts.mapNotNull { esdt ->
            val identifierParts = esdt.tokenIdentifier.split("-")
            val commonIdentifier = identifierParts.take(2).joinToString("-")
            (sfts[commonIdentifier]
                ?: getTokenProperties(commonIdentifier)
                    .takeIf { it.type == TokenType.SFT })?.let {
                Token(
                    value = Value.extract(esdt.balance, commonIdentifier) ?: Value.zero(identifierParts.first()),
                    properties = it
                )
            }
        }
    }.also {
        endCustomTrace("SftsForAddress:$address")
    }

    suspend fun getSftWithId(id: String): TokenProperties? =
        Sfts.all().value.firstOrNull { it.identifier == id }

    suspend fun getTokenWithId(id: String): TokenProperties? = coroutineScope {
        val realID = id.split("-").take(2).joinToString("-")

        val esdtsDeferred = async { Esdts.all().value }
        val nftsDeferred = async { Nfts.all().value }
        val sftsDeferred = async { Sfts.all().value }

        getTokenPropertiesForId(realID, esdtsDeferred.await())
            ?: getTokenPropertiesForId(realID, sftsDeferred.await())
            ?: getTokenPropertiesForId(realID, nftsDeferred.await())
    }

    suspend fun getDecimalsForToken(id: String): Int =
        if (id == "EGLD") {
            18
        } else {
            getTokenWithId(id)?.decimals ?: 18
        }

    private fun getTokenPropertiesForId(id: String, tokens: List<TokenProperties>): TokenProperties? =
        tokens.find { it.identifier == id }

    private suspend fun getTokenProperties(id: String, collection: String? = null): TokenProperties {
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

        val tokenType = when (val type = properties?.getOrNull(1) ?: "") {
            "NonFungibleESDT" -> TokenType.NFT
            "SemiFungibleESDT" -> TokenType.SFT
            "MetaESDT" -> TokenType.MetaESDT
            "FungibleESDT" -> TokenType.ESDT
            else -> {
                logger.error { "Error: unknown token type: $type" }
                TokenType.ESDT
            }
        }

        return TokenProperties(
            identifier = collection ?: id,
            collection = collection?.let { id },
            name = properties?.getOrNull(0) ?: "",
            type = tokenType,
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
            canAddSpecialRoles = !tokenType.isEsdt && properties?.getOrNull(14)
                ?.toBooleanStrictOrNull() ?: false,
            canTransferNFTCreateRole = tokenType != TokenType.ESDT && properties?.getOrNull(15)
                ?.toBooleanStrictOrNull() ?: false,
            nftCreateStopped = tokenType != TokenType.ESDT && properties?.getOrNull(16)
                ?.toBooleanStrictOrNull() ?: false,
            wiped = if (tokenType != TokenType.ESDT) null else properties?.getOrNull(17) ?: ""
        )
    }

    private const val NUM_PARALLEL_FETCH = 100

    val logger = KotlinLogging.logger {}
}

@Serializable
data class Esdts(
    val value: List<TokenProperties>
) {
    companion object {
        suspend fun all(skipCache: Boolean = false) = if (skipCache) {
            TokenRepository.getAllEsdts().also { putInCache(CacheType.Esdts, it) }
        } else {
            withCache(CacheType.Esdts) { TokenRepository.getAllEsdts() }
        }
    }
}

@Serializable
data class Nfts(
    val value: List<TokenProperties>
) {
    companion object {
        suspend fun all(skipCache: Boolean = false) = if (skipCache) {
            TokenRepository.getAllNfts().also { putInCache(CacheType.Nfts, it) }
        } else {
            withCache(CacheType.Nfts) { TokenRepository.getAllNfts() }
        }
    }
}

@Serializable
data class Sfts(
    val value: List<TokenProperties>
) {
    companion object {
        suspend fun all(skipCache: Boolean = false) = if (skipCache) {
            TokenRepository.getAllSfts().also { putInCache(CacheType.Sfts, it) }
        } else {
            withCache(CacheType.Sfts) { TokenRepository.getAllSfts() }
        }
    }
}
