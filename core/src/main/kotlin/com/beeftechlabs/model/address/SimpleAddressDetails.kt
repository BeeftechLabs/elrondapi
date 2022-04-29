package com.beeftechlabs.model.address

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class SimpleAddressDetails(
    val address: String,
    val balance: Value
)
