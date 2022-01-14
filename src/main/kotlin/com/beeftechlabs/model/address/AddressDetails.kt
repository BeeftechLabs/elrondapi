package com.beeftechlabs.model.address

import com.beeftechlabs.model.token.Token
import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class AddressDetails(
    val address: String,
    val nonce: Long,
    val balance: Value,
    val herotag: String,
    val ownerAddress: String,
    val tokens: List<Token>,
    val delegations: List<AddressDelegation>
)
