package com.beeftechlabs.repository.elastic.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticAddress(
    @JsonProperty("address") val address: String,
    @JsonProperty("balance") val balance: String,
    @JsonProperty("balanceNum") val balanceNum: Double,
    @JsonProperty("totalBalanceWithStake") val totalBalanceWithStake: String,
    @JsonProperty("totalBalanceWithStakeNum") val totalBalanceWithStakeNum: Double
)
