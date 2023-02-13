package com.beeftechlabs.model.token

import com.beeftechlabs.googlestorage.TokenAssets
import kotlinx.serialization.Serializable

@Serializable
data class TokenProperties(
    val identifier: String,
    val collection: String?,
    val name: String,
    val type: TokenType,
    val owner: String,
    val minted: String,
    val burnt: String,
    val decimals: Int,
    val isPaused: Boolean,
    val canUpgrade: Boolean,
    val canMint: Boolean,
    val canBurn: Boolean,
    val canChangeOwner: Boolean,
    val canPause: Boolean,
    val canFreeze: Boolean,
    val canWipe: Boolean,
    val canAddSpecialRoles: Boolean,
    val canTransferNFTCreateRole: Boolean,
    val nftCreateStopped: Boolean,
    val wiped: String?,
    val assets: TokenAssets? = null
) {
    companion object {
        val DEFAULT = TokenProperties(
            identifier = "",
            collection = null,
            name = "",
            type = TokenType.ESDT,
            owner = "",
            minted = "",
            burnt = "",
            decimals = 0,
            isPaused = false,
            canUpgrade = false,
            canMint = false,
            canBurn = false,
            canChangeOwner = false,
            canPause = false,
            canFreeze = false,
            canWipe = false,
            canAddSpecialRoles = false,
            canTransferNFTCreateRole = false,
            nftCreateStopped = false,
            wiped = null,
            assets = null
        )
    }
}