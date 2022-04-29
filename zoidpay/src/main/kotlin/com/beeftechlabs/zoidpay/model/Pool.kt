package com.beeftechlabs.zoidpay.model

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class Pool(
    val id: String,
    val owner: String,
    val totalStaked: Value,
    val zoidsters: Long,
    val name: String,
    val status: Int
)
