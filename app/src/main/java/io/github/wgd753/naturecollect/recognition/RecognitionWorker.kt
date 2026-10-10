package io.github.wgd753.naturecollect.recognition

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.wgd753.naturecollect.NatureApp

class RecognitionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = (applicationContext as NatureApp).container.recognition
        val needsRetry = service.runPending()
        return if (needsRetry && runAttemptCount < 5) Result.retry() else Result.success()
    }
}
