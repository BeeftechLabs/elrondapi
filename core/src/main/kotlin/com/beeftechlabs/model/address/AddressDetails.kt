package com.beeftechlabs.model.address

import com.beeftechlabs.model.core.Unstaked
import com.beeftechlabs.model.token.Token
import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class AddressDetails(
    val address: String,
    val balance: Value,
    val nonce: Long? = null,
    val herotag: String? = null,
    val ownerAddress: String? = null,
    val tokens: List<Token>? = null,
    val nfts: List<Token>? = null,
    val sfts: List<Token>? = null,
    val delegations: List<AddressDelegation>? = null,
    val staked: Value? = null,
    val unstaked: List<Unstaked>? = null
)
