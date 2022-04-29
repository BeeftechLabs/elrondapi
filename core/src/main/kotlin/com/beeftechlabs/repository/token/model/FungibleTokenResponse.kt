package com.beeftechlabs.repository.token.model

import kotlinx.serialization.Serializable

@Serializable
data class FungibleTokenResponse(
    val data: FungibleTokensWrapper
)

@Serializable
data class FungibleTokensWrapper(
    val tokens: List<String>
)