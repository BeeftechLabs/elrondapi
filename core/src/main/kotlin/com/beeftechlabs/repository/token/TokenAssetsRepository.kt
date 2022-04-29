package com.beeftechlabs.repository.token

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.peekCache
import com.beeftechlabs.cache.putInCache
import com.beeftechlabs.config
import com.beeftechlabs.googlestorage.GoogleStorageConnector
import com.beeftechlabs.googlestorage.TokenAssets
import kotlinx.serialization.Serializable

object TokenAssetsRepository {

    private val storageConnector by lazy {
        if (config.hasGoogleStorage) {
            GoogleStorageConnector(config.googleStorage?.bucket ?: "")
        } else {
            null
        }
    }

    fun getAllAssets(): Map<String, TokenAssets> {
        return storageConnector?.getAllTokenAssets() ?: emptyMap()
    }
}

@Serializable
data class AllTokenAssets(
    val value: Map<String, TokenAssets>
) {
    companion object {
        suspend fun get(skipCache: Boolean = false) = if (skipCache) {
            AllTokenAssets(TokenAssetsRepository.getAllAssets()).also { putInCache(CacheType.TokenAssets, it) }
        } else {
            // We don't do asset refresh when expired, assets get refreshed by external call
            peekCache<AllTokenAssets>(CacheType.TokenAssets) ?: AllTokenAssets(emptyMap())
        }
    }
}