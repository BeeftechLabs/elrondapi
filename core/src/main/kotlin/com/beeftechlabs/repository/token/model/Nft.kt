package com.beeftechlabs.repository.token.model

import com.beeftechlabs.model.token.TokenData
import kotlinx.serialization.Serializable

@Serializable
data class GetNftResponse(
    val data: NftWrapper
)

@Serializable
data class NftWrapper(
    val tokenData: TokenData
)
