package com.beeftechlabs.model.smartcontract

import kotlinx.serialization.Serializable

@Serializable
data class ScQueryRequest(
    val scAddress: String,
    val funcName: String,
    val args: List<String>? = null,
    val caller: String? = null
)