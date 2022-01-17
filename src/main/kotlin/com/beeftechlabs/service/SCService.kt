package com.beeftechlabs.service

import com.beeftechlabs.model.smartcontract.ScQueryRequest

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

        return response.data.data.returnData?.mapNotNull { it } ?: emptyList()
    }
}