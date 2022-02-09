package com.beeftechlabs.googlestorage

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class GoogleStorageConnector(private val bucket: String) {

    private val storage: Storage by lazy { StorageOptions.getDefaultInstance().service }
    private val baseBucket by lazy { storage.get(bucket) }
    private val json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }

    private fun getTokenAssets(assetPath: String): TokenAssets? {
        val info = String(baseBucket.get("$assetPath/info.json").getContent())
        val assets = try {
            json.decodeFromString<TokenAssets>(info)
        } catch (exception: Exception) {
            println(exception)
            return null
        }

        val pngUrl = baseBucket.get("$assetPath/logo.png").signUrl(
            1,
            TimeUnit.DAYS,
            Storage.SignUrlOption.withV4Signature()
        )

        val svgUrl = baseBucket.get("$assetPath/logo.svg").signUrl(
            1,
            TimeUnit.DAYS,
            Storage.SignUrlOption.withV4Signature()
        )

        return assets.copy(
            pngUrl = pngUrl.toString(),
            svgUrl = svgUrl.toString()
        )
    }

    fun getAllTokenAssets(): Map<String, TokenAssets> {
        return storage.list(
            bucket,
            Storage.BlobListOption.prefix(TOKENS_PREFIX),
            Storage.BlobListOption.delimiter("/")
        ).iterateAll().mapNotNull { blob ->
            val path = blob.name.dropLast(1)
            val id = path.split("/").last()
            getTokenAssets(path)?.let { id to it }
        }.toMap()
    }

    companion object {
        private const val TOKENS_PREFIX = "tokens/"
    }
}