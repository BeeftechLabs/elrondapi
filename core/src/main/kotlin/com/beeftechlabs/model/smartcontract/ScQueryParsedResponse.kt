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
    val long: Long?,
    val double: Double?,
    val hex: String?,
    val boolean: Boolean?
) {
    companion object {
        fun fromString(result: String) = ScQueryResultData(
            string = tryOrNull { result.fromBase64String() },
            bigNumber = tryOrNull { result.fromBase64ToHexString().bigInteger().toString() },
            long = tryOrNull { result.fromBase64ToHexString().toLong(16) },
            double = tryOrNull { result.fromBase64ToHexString().bigDecimal().toDouble() },
            hex = tryOrNull { result.fromBase64ToHexString() },
            boolean = tryOrNull { result.fromBase64ToHexString().toInt(16) > 0 }
        )
    }
}