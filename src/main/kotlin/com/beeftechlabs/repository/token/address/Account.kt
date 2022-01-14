package com.beeftechlabs.repository.token.address

import kotlinx.serialization.Serializable

@Serializable
data class GetAccountResponse(
    val data: AccountWrapper
)

@Serializable
data class AccountWrapper(
    val account: Account
)

@Serializable
data class Account(
    val address: String,
    val nonce: Long,
    val balance: String,
    val username: String,
    val ownerAddress: String
)
