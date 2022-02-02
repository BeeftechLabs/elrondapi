package com.beeftechlabs.repository.elastic.model

data class ElasticResult<T>(
    val data: List<T>,
    val hasMore: Boolean
)
