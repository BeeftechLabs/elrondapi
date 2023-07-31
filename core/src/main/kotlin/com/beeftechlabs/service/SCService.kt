package com.beeftechlabs.service

import com.beeftechlabs.model.smartcontract.ScQueryParsedResponse
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.smartcontract.ScQueryResultData
import com.beeftechlabs.util.*

object SCService {

    suspend fun vmQuery(
        contract: String,
        function: String,
        args: List<String>? = null,
        caller: String? = null
    ): List<String> {
        val response = GatewayService.vmQuery(
            ScQueryRequest(
                scAddress = contract,
                funcName = function,
                args = args,
                caller = caller
            )
        )

        return response.returnData?.mapNotNull { it } ?: emptyList()
    }

    suspend fun vmQueryParsed(
        request: ScQueryRequest
    ): ScQueryParsedResponse {
        val response = GatewayService.vmQuery(request)

        return ScQueryParsedResponse(
            response.returnData?.mapNotNull { it }?.map(ScQueryResultData::fromString) ?: emptyList()
        )
    }

    suspend fun vmQueryDouble(
        contract: String,
        function: String,
        args: List<String>? = null,
        caller: String? = null
    ): Double? =
        vmQuery(contract, function, args, caller).firstOrNull()?.fromBase64ToHexString()?.bigDecimal()?.toDouble()

    suspend fun vmQueryBigInt(
        contract: String,
        function: String,
        args: List<String>? = null,
        caller: String? = null
    ): String? =
        vmQuery(contract, function, args, caller).firstOrNull()?.fromBase64ToHexString()?.bigInteger()?.toString()

    suspend fun vmQueryString(
        contract: String,
        function: String,
        args: List<String>? = null,
        caller: String? = null
    ): String? = vmQuery(contract, function, args, caller).firstOrNull()?.fromBase64String()

    suspend fun vmQueryInt(
        contract: String,
        function: String,
        args: List<String>? = null,
        caller: String? = null
    ): Int? = vmQuery(contract, function, args, caller).firstOrNull()?.fromBase64ToHexString()?.toInt(16)
}