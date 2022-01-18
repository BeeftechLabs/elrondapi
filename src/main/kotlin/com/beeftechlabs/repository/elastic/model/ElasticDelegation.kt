package com.beeftechlabs.repository.elastic.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticDelegation(
    @JsonProperty("address") val address: String,
    @JsonProperty("contract") val contract: String,
    @JsonProperty("activeStake") val activeStake: String,
)
