package com.beeftechlabs.model.address

import kotlinx.serialization.Serializable

@Serializable
data class AddressesResponse(
    val hasMore: Boolean,
    val requestId: String,
    val lastResult: String,
    val addresses: List<AddressDetails>
)
