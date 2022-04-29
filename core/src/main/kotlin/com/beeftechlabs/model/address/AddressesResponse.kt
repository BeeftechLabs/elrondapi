package com.beeftechlabs.model.address

import kotlinx.serialization.Serializable

@Serializable
data class AddressesResponse(
    val hasMore: Boolean,
    val requestId: String? = null,
    val firstResult: String? = null,
    val lastResult: String? = null,
    val addresses: List<SimpleAddressDetails>
)
