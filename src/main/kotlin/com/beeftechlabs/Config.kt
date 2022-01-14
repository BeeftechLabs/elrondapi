package com.beeftechlabs

data class Config(
    val port: Int,
    val elastic: Elastic?,
    val maxPageSize: Int,
    val elrond: Elrond?
) {
    val hasElastic = elastic != null
    val hasElrondConfig = elrond != null
}

data class Elastic(
    val url: String,
    val username: String,
    val password: String
)

data class Elrond(
    val proxy: String,
    val delegationManager: String,
    val staking: String,
    val auction: String,
    val esdt: String
)
