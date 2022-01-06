package com.beeftechlabs

data class Config(
    val port: Int,
    val elastic: Elastic,
    val maxPageSize: Int
)

data class Elastic(
    val url: String,
    val username: String,
    val password: String
)
