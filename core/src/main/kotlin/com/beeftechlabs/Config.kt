package com.beeftechlabs

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

val config = ConfigLoader().loadConfigOrThrow<Config>(
    listOfNotNull(
        File("config-zoidpay.yaml").takeIf { it.exists() },
        File("config-dev.yaml").takeIf { it.exists() },
        File("config.yaml")
    )
)

data class Config(
    val port: Int,
    val elastic: Elastic? = null,
    val maxPageSize: Int,
    val elrond: Elrond? = null,
    val redis: Redis? = null,
    val memoryStore: Boolean,
    val secret: String = "",
    val traceCalls: Boolean = false,
    val googleStorage: GoogleStorage? = null,
    val zoidpay: ZoidPayConfig? = null,
    val logApiCalls: Boolean,
) {
    val hasElastic = elastic != null
    val hasElrondConfig = elrond != null
    val hasGoogleStorage = googleStorage?.enabled == true
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
    val esdt: String,
    val mdexPair: String
)

data class Redis(
    val enabled: Boolean,
    val host: String,
    val port: Int
)

data class GoogleStorage(
    val enabled: Boolean,
    val bucket: String
)

data class ZoidPayConfig(
    val tokenId: String,
    val stakingSC: String,
    val pool: String
)
