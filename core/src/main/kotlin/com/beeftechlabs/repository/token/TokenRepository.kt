package com.beeftechlabs.repository.token

import com.beeftechlabs.cache.*
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.token.*
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.repository.token.model.FungibleTokenResponse
import com.beeftechlabs.repository.token.model.GetEsdtsResponse
import com.beeftechlabs.repository.token.model.GetNftResponse
import com.beeftechlabs.service.GatewayService
import com.beeftechlabs.util.*
import kotlinx.coroutines.Deferred
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

    suspend fun getEsdtsForAddress(address: String, process: Boolean): List<Token> = coroutineScope {
        startCustomTrace("TokensForAddress:$address")
        val addressEsdtsDeferred =
            async { tryCoroutineOrDefault(emptyList()) { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values } }
        val esdtsDeferred = async { if (process) { Esdts.all() } else { Esdts(emptyList()) } }

        val assets = AllTokenAssets.get().value
        val esdts = esdtsDeferred.await().value.associateBy { it.identifier }
        val addressEsdts = addressEsdtsDeferred.await()

        if (process) {
            val esdtsWithoutData = addressEsdts.mapNotNull { esdt ->
                val identifierParts = esdt.tokenIdentifier.split("-")
                val commonIdentifier = identifierParts.take(2).joinToString("-")
                (esdts[commonIdentifier]
                    ?: getTokenProperties(commonIdentifier)
                        .takeIf { it.type == TokenType.ESDT || it.type == TokenType.MetaESDT }?.copy(
                            assets = assets[commonIdentifier]
                        ))?.let {
                    Token(
                        value = Value.extract(esdt.balance, esdt.tokenIdentifier),
                        properties = it,
                        data = null
                    )
                }
            }
            esdtsWithoutData.chunked(NUM_PARALLEL_FETCH)
                .map { chunk ->
                    chunk.map { token ->
                        async {
                            val tokenData = getNftData(address, token.value.token)
                            token.copy(data = tokenData.copy(
                                attributes = tokenData.attributes,
                                uris = tokenData.uris?.map { it.fromBase64String() }
                            ))
                        }
                    }.awaitAll()
                }.flatten()
        } else {
            addressEsdts.map { esdt ->
                Token(
                    value = Value.extract(esdt.balance, esdt.tokenIdentifier),
                    properties = TokenProperties.DEFAULT,
                    data = null
                )
            }
        }
    }.also {
        endCustomTrace("TokensForAddress:$address")
    }

    suspend fun getEsdtWithId(id: String): TokenProperties? =
        Esdts.all().value.firstOrNull { it.identifier == id }

    suspend fun getNftsForAddress(address: String, process: Boolean): List<Token> = coroutineScope {
        startCustomTrace("NftsForAddress:$address")
        val esdtsDeferred =
            async { tryCoroutineOrDefault(emptyList()) { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values } }
        val nftsDeferred = async { if (process) { Nfts.all() } else { Nfts(emptyList()) } }

        val esdts = esdtsDeferred.await()
        val nfts = nftsDeferred.await().value.associateBy { it.identifier }

        if (process) {
            val nftsWithoutData = esdts.mapNotNull { esdt ->
                val identifierParts = esdt.tokenIdentifier.split("-")
                val commonIdentifier = identifierParts.take(2).joinToString("-")
                (nfts[commonIdentifier]
                    ?: getTokenProperties(commonIdentifier)
                        .takeIf { it.type == TokenType.NFT })?.let {
                    Token(
                        value = Value.extract(esdt.balance, esdt.tokenIdentifier),
                        properties = it,
                        data = null
                    )
                }
            }

            nftsWithoutData.chunked(NUM_PARALLEL_FETCH)
                .map { chunk ->
                    chunk.map { token ->
                        async {
                            val tokenData = getNftData(address, token.value.token)
                            token.copy(data = tokenData.copy(
                                attributes = tokenData.attributes,
                                uris = tokenData.uris?.map { it.fromBase64String() }
                            ))
                        }
                    }.awaitAll()
                }.flatten()
        } else {
            esdts.map { esdt ->
                Token(
                    value = Value.extract(esdt.balance, esdt.tokenIdentifier),
                    properties = TokenProperties.DEFAULT,
                    data = null
                )
            }
        }
    }.also {
        endCustomTrace("NftsForAddress:$address")
    }

    private suspend fun getNftData(address: String, id: String): TokenData {
        val parts = id.split("-")
        val nonce = parts.last().toInt(16)
        val collection = parts.take(2).joinToString("-")

        return GatewayService.get<GetNftResponse>("address/$address/nft/$collection/nonce/$nonce").data.tokenData
    }

    suspend fun getNftWithCollectionId(id: String): Token? {
        val isCollection = id.count { it == '-' } == 1

        if (isCollection) {
            val props = Nfts.all().value.firstOrNull { it.identifier == id }
            if (props != null) {
                return Token(
                    properties = props,
                    value = Value.None,
                    data = null
                )
            }
        } else {
            // TODO
        }
        return null
    }

    suspend fun getSftsForAddress(address: String, process: Boolean): List<Token> = coroutineScope {
        startCustomTrace("SftsForAddress:$address")
        val esdtsDeferred =
            async { tryCoroutineOrDefault(emptyList()) { GatewayService.get<GetEsdtsResponse>("address/$address/esdt").data.esdts.values } }
        val sftsDeferred = async { if (process) { Sfts.all() } else { Sfts(emptyList()) } }

        val esdts = esdtsDeferred.await()
        val sfts = sftsDeferred.await().value.associateBy { it.identifier }

        if (process) {
            esdts.mapNotNull { esdt ->
                val identifierParts = esdt.tokenIdentifier.split("-")
                val commonIdentifier = identifierParts.take(2).joinToString("-")
                (sfts[commonIdentifier]
                    ?: getTokenProperties(commonIdentifier)
                        .takeIf { it.type == TokenType.SFT })?.let {
                    Token(
                        value = Value.extract(esdt.balance, commonIdentifier) ?: Value.zero(identifierParts.first()),
                        properties = it,
                        data = null
                    )
                }
            }
        } else {
            esdts.map { esdt ->
                Token(
                    value = Value.extract(esdt.balance, esdt.tokenIdentifier),
                    properties = TokenProperties.DEFAULT,
                    data = null
                )
            }
        }
    }.also {
        endCustomTrace("SftsForAddress:$address")
    }

    suspend fun getSftWithId(id: String): TokenProperties? =
        Sfts.all().value.firstOrNull { it.identifier == id }

    private suspend fun getTokenPropertiesWithId(id: String): TokenProperties = coroutineScope {
        val realID = id.split("-").take(2).joinToString("-")

        val esdtsDeferred: Deferred<Esdts?> = async { peekCache(CacheType.Esdts) }
        val nftsDeferred: Deferred<Nfts?> = async { peekCache(CacheType.Nfts) }
        val sftsDeferred: Deferred<Sfts?> = async { peekCache(CacheType.Sfts) }

        val esdts = esdtsDeferred.await()?.value ?: emptyList()
        val sfts = sftsDeferred.await()?.value ?: emptyList()
        val nfts = nftsDeferred.await()?.value ?: emptyList()

        getTokenPropertiesForId(realID, esdts)
            ?: getTokenPropertiesForId(realID, sfts)
            ?: getTokenPropertiesForId(realID, nfts)
            ?: getTokenProperties(id.collectionId()).also { props ->
                when (props.type) {
                    TokenType.ESDT, TokenType.MetaESDT -> updateInCache(CacheType.Esdts, Esdts(esdts + props))
                    TokenType.SFT -> updateInCache(CacheType.Sfts, Sfts(sfts + props))
                    TokenType.NFT -> updateInCache(CacheType.Nfts, Nfts(nfts + props))
                }
            }
    }

    suspend fun getDecimalsForToken(id: String): Int =
        if (id == "EGLD") {
            18
        } else {
            getTokenPropertiesWithId(id).decimals
        }

    private fun getTokenPropertiesForId(id: String, tokens: List<TokenProperties>?): TokenProperties? =
        tokens?.find { it.identifier == id }

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

    private val logger = KotlinLogging.logger {}
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
