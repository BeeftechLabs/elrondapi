package com.beeftechlabs.model.smartcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScQueryRequest(
    @SerialName("ScAddress") val scAddress: String,
    @SerialName("FuncName") val funcName: String,
    @SerialName("Args") val args: List<String>? = null,
    @SerialName("Caller") val caller: String? = null
)