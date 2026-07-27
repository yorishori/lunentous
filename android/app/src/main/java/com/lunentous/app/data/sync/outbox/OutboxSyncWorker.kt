package com.lunentous.app.data.sync.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lunentous.app.LunentousApplication

/**
 * Drains the outbox queue. Reaches into LunentousApplication.container
 * directly rather than a custom WorkerFactory -- the default factory only
 * needs a (Context, WorkerParameters) constructor, and applicationContext
 * already gets us to the one AppContainer instance, so a factory would add
 * indirection without buying anything here.
 */
class OutboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val processor = (applicationContext as LunentousApplication).container.outboxProcessor
        val drained = processor.processQueue()
        // A partial drain (retryable failure or reauth) isn't a Worker
        // failure -- it's an expected "try again later" state, so WorkManager
        // shouldn't apply its own failure backoff on top of ours.
        return if (drained) Result.success() else Result.retry()
    }
}
