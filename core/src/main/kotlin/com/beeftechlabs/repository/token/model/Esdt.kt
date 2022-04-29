package com.beeftechlabs.repository.token.model

import kotlinx.serialization.Serializable

@Serializable
data class GetEsdtsResponse(
    val data: EsdtsWrapper
)

@Serializable
data class EsdtsWrapper(
    val esdts: Map<String, Esdt>
)

@Serializable
data class Esdt(
    val attributes: String? = null,
    val balance: String,
    val creator: String? = null,
    val nonce: Long? = null,
    val royalties: String? = null,
    val tokenIdentifier: String,
    val uris: List<String>? = null
)
