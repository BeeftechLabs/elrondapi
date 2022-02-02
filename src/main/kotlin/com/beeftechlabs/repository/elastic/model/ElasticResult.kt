package com.beeftechlabs.repository.elastic.model

data class ElasticResult<T>(
    val data: List<ElasticItem<T>>,
    val hasMore: Boolean
)

data class ElasticItem<T>(
    val id: String,
    val item: T
)
