package com.beeftechlabs.util

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture

suspend inline fun <reified T> CompletableFuture<T>.suspending(): T = suspendCancellableCoroutine { continuation ->
    thenAccept { data ->
        continuation.resumeWith(Result.success(data))
    }.exceptionally { exception ->
        continuation.resumeWith(Result.failure(exception))
        null
    }
}