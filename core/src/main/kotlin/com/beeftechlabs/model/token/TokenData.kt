package com.beeftechlabs.model.token

import kotlinx.serialization.Serializable

@Serializable
data class TokenData(
    val attributes: String? = null,
    val balance: String,
    val creator: String? = null,
    val name: String? = null,
    val nonce: Long? = null,
    val royalties: String? = null,
    val tokenIdentifier: String,
    val uris: List<String>? = null
)
