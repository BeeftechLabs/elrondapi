package com.beeftechlabs.googlestorage

import kotlinx.serialization.Serializable

@Serializable
data class TokenAssets(
    val website: String,
    val description: String,
    val social: TokenSocialAssets = TokenSocialAssets(),
    val status: String,
    val pngUrl: String = "",
    val svgUrl: String = ""
)

@Serializable
data class TokenSocialAssets(
    val email: String = "",
    val blog: String = "",
    val twitter: String = "",
    val whitepaper: String = "",
    val coinmarketcap: String = "",
    val coingecko: String = ""
)
