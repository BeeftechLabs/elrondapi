package com.beeftechlabs.model.smartcontract

import com.beeftechlabs.util.*
import kotlinx.serialization.Serializable

@Serializable
data class ScQueryParsedResponse(
    val output: List<ScQueryResultData>
)

@Serializable
data class ScQueryResultData(
    val string: String?,
    val bigNumber: String?,
    val int: Int?,
    val double: Double?
) {
    companion object {
        fun fromString(result: String) = ScQueryResultData(
            string = tryOrNull { result.fromBase64String() },
            bigNumber = tryOrNull { result.fromBase64ToHexString().bigInteger().toString() },
            int = tryOrNull { result.fromBase64ToHexString().toInt(16) },
            double = tryOrNull { result.fromBase64ToHexString().bigDecimal().toDouble() },
        )
    }
}