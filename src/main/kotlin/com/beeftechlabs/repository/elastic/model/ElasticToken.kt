package com.beeftechlabs.repository.elastic.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

@Serializable
data class ElasticToken(
    @JsonProperty("identifier") val identifier: String,
    @JsonProperty("token") val token: String,
    @JsonProperty("nonce") val nonce: Long,
    @JsonProperty("timestamp") val timestamp: Long,
    @JsonProperty("data") val data: ElasticTokenData
)

@Serializable
data class ElasticTokenData(
    @JsonProperty("name") val name: String,
    @JsonProperty("creator") val creator: String,
    @JsonProperty("attributes") val attributes: String,
    @JsonProperty("royalties") val royalties: Int,
    @JsonProperty("uris") val uris: List<String>,
    @JsonProperty("tags") val tags: List<String>
)
