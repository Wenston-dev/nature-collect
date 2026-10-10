package io.github.wgd753.naturecollect.recognition

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.wgd753.naturecollect.data.CaptureRepository
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.Settings
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.ml.StickerMaker
import java.util.concurrent.TimeUnit

/** Runs recognition for captures and schedules retries when offline. */
class RecognitionService(
    private val context: Context,
    private val repository: CaptureRepository,
    private val media: MediaFiles,
    private val settings: Settings,
    private val recognizer: DeepSeekRecognizer,
) {
    /** Queues recognition of everything pending; runs as soon as there is a network. */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<RecognitionWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Recognises every capture still waiting. Returns true if some failed in a
     * way worth retrying later (e.g. the network dropped).
     */
    suspend fun processPending(): Boolean {
        val apiKey = settings.apiKey.value
        var retry = false
        for (capture in repository.unrecognized()) {
            if (apiKey.isNullOrBlank()) {
                repository.update(capture.copy(status = RecognitionStatus.PENDING, errorMessage = "还没有设置 DeepSeek API Key"))
                continue
            }
            if (capture.status == RecognitionStatus.FAILED) continue
            repository.update(capture.copy(status = RecognitionStatus.RUNNING, errorMessage = null))
            try {
                val sticker = BitmapFactory.decodeFile(media.file(capture.stickerFile).path)
                    ?: error("贴纸文件丢失")
                val result = recognizer.recognize(
                    apiKey = apiKey,
                    image = StickerMaker.onWhite(sticker),
                    latitude = capture.latitude,
                    longitude = capture.longitude,
                    takenAt = capture.createdAt,
                )
                // The user may have renamed it while we waited; their name wins.
                val latest = repository.get(capture.id) ?: continue
                if (latest.status == RecognitionStatus.MANUAL) continue
                repository.update(
                    latest.copy(
                        nameZh = result.nameZh,
                        nameSci = result.nameSci,
                        category = result.category.name,
                        confidence = result.confidence,
                        description = result.description,
                        alternatives = result.alternatives.joinToString("|").ifEmpty { null },
                        status = RecognitionStatus.DONE,
                        errorMessage = null,
                    ),
                )
            } catch (e: RecognitionException) {
                retry = retry || e.retryable
                val status = if (e.retryable) RecognitionStatus.PENDING else RecognitionStatus.FAILED
                repository.get(capture.id)?.let { repository.update(it.copy(status = status, errorMessage = e.message)) }
            } catch (e: Exception) {
                repository.get(capture.id)?.let {
                    repository.update(it.copy(status = RecognitionStatus.FAILED, errorMessage = e.message ?: "识别失败"))
                }
            }
        }
        return retry
    }

    /** Puts failed captures back in the queue (after the user fixes the key, for example). */
    suspend fun retry(id: String) {
        repository.markPending(id)
        schedule()
    }

    private companion object {
        const val WORK_NAME = "recognize-captures"
    }
}
