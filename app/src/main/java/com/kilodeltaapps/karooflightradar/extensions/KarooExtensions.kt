package com.kilodeltaapps.karooflightradar.extensions

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Converts KarooSystemService.addConsumer into a Flow<StreamState>
 * Used in the official sample.
 */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        trySendBlocking(event.state)
    }
    awaitClose { removeConsumer(listenerId) }
}

/**
 * Generic consumer flow for any KarooEvent
 */
inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val listenerId = addConsumer<T> { trySend(it) }
    awaitClose { removeConsumer(listenerId) }
}