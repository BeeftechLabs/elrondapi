package com.beeftechlabs.model.token

import com.beeftechlabs.repository.elastic.model.ElasticToken

data class TokenRequest(
    val timestamp: Long? = null,
    val newer: Boolean = true,
    val size: Int = 100
)

data class TokenResponse(
    val tokens: List<ElasticToken>,
    val hasMore: Boolean = true,
    val lastTimestamp: Long? = null
)