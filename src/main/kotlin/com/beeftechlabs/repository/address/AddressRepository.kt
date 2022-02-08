package com.beeftechlabs.repository.address

import com.beeftechlabs.model.address.AddressDetails
import com.beeftechlabs.model.address.AddressesResponse
import com.beeftechlabs.model.core.LongValue
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.repository.StakingRepository
import com.beeftechlabs.repository.address.model.Account
import com.beeftechlabs.repository.address.model.AddressSort
import com.beeftechlabs.repository.address.model.GetAccountResponse
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.service.GatewayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object AddressRepository {

    suspend fun getAddressDetails(
        address: String,
        withDelegations: Boolean,
        withTokens: Boolean,
        withNfts: Boolean,
        withSfts: Boolean,
        withStake: Boolean
    ): AddressDetails = coroutineScope {
        startCustomTrace("AddressDetails:$address")
        val tokens = async { if (withTokens) TokenRepository.getEsdtsForAddress(address) else null }
        val nfts = async { if (withNfts) TokenRepository.getNftsForAddress(address) else null }
        val sfts = async { if (withSfts) TokenRepository.getSftsForAddress(address) else null }
        val delegations = async { if (withDelegations) StakingRepository.getDelegations(address) else null }
        val staked = async { if (withStake) StakingRepository.getStaked(address) else null }
        val account = withContext(Dispatchers.IO) { getAccountFromGateway(address) }

        val totalStaked = staked.await()?.first
        val unstaked = staked.await()?.second

        AddressDetails(
            address = address,
            nonce = account.nonce,
            balance = Value.extract(account.balance, "EGLD"),
            herotag = account.username,
            ownerAddress = account.ownerAddress,
            tokens = tokens.await(),
            nfts = nfts.await(),
            sfts = sfts.await(),
            delegations = delegations.await(),
            staked = totalStaked,
            unstaked = unstaked
        )
    }.also {
        endCustomTrace("AddressDetails:$address")
    }

    suspend fun getAddressBalance(address: String): Value = Value.extract(
        getAccountFromGateway(address).balance,
        "EGLD"
    )

    suspend fun getAddressNonce(address: String): LongValue =
        LongValue(getAccountFromGateway(address).nonce)

    suspend fun getAddresses(
        sort: AddressSort,
        pageSize: Int,
        filter: String?,
        requestId: String?,
        startingWith: String?
    ): AddressesResponse {
        startCustomTrace("GetAddresses:$sort:$pageSize:$filter")
        return ElasticRepository.getAddressesPaged(sort, pageSize, filter, requestId, startingWith).also {
            endCustomTrace("GetAddresses:$sort:$pageSize:$filter")
        }
    }

    private suspend fun getAccountFromGateway(address: String): Account =
        GatewayService.get<GetAccountResponse>("address/$address").data.account
}