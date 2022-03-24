package com.beeftechlabs.model.delegation

import kotlinx.serialization.Serializable

@Serializable
data class DelegatorsResponse(
    val hasMore: Boolean,
    val requestId: String? = null,
    val firstResult: String? = null,
    val lastResult: String? = null,
    val delegators: List<Delegator>
)
