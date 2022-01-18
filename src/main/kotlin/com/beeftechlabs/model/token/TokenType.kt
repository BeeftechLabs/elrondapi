package com.beeftechlabs.model.token

import kotlinx.serialization.Serializable

@Serializable
enum class TokenType {
    ESDT, SFT, NFT, MetaESDT;

    val isEsdt get() = this == ESDT || this == MetaESDT
}