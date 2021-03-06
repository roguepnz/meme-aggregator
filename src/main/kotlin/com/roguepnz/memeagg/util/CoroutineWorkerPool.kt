package com.roguepnz.memeagg.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

private typealias Task = suspend () -> Unit

class CoroutineWorkerPool(workers: Int) {

    private val queue = Channel<Task>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        repeat(workers) {
            scope.launch {
                for (task in queue) {
                    task()
                }
            }
        }
    }

    fun <T> submitAsync(action: suspend () -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        queue.offer {
            try {
                deferred.complete(action())
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    suspend fun <T> submit(action: suspend () -> T): T {
        return submitAsync(action).await()
    }
}