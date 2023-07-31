package com.beeftechlabs.model.smartcontract

import kotlinx.serialization.Serializable

@Serializable
data class ScQueryResponse(
    val data: ScQueryDataWrapper
)

@Serializable
data class ScQueryDataWrapper(
    val data: ScQueryData
)

@Serializable
data class ScQueryData(
    val returnData: List<String?>? = null,
    val returnCode: String? = null,
    val returnMessage: String? = null
)
