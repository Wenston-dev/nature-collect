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
import io.github.wgd753.naturecollect.location.PlaceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Runs recognition for captures and schedules retries when offline. */
class RecognitionService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: CaptureRepository,
    private val media: MediaFiles,
    private val settings: Settings,
    private val recognizer: DeepSeekRecognizer,
    private val places: PlaceResolver,
) {
    private val mutex = Mutex()

    /**
     * Recognises everything pending right away, in the app's own scope so it
     * keeps going when the user leaves the screen. Anything that fails for a
     * temporary reason is handed to WorkManager to retry once there's a network.
     */
    fun schedule() {
        scope.launch {
            val retry = runPending()
            if (retry) scheduleRetry()
        }
    }

    /** Serialised so the in-app run and a WorkManager run never work on the same capture. */
    suspend fun runPending(): Boolean = mutex.withLock {
        resolvePlaces()
        processPending()
    }

    /** Captures left "running" by a killed process go back in the queue. */
    suspend fun recoverInterrupted() {
        repository.unrecognized().filter { it.status == RecognitionStatus.RUNNING }.forEach {
            repository.update(it.copy(status = RecognitionStatus.PENDING))
        }
    }

    private suspend fun resolvePlaces() {
        val todo = repository.missingPlaces()
        for ((index, capture) in todo.withIndex()) {
            val lat = capture.latitude ?: continue
            val lon = capture.longitude ?: continue
            val name = places.resolve(lat, lon) ?: continue
            val latest = repository.get(capture.id) ?: continue
            if (latest.placeName == null) repository.update(latest.copy(placeName = name))
            // Nominatim allows one request a second.
            if (index < todo.lastIndex) delay(1100)
        }
    }

    private fun scheduleRetry() {
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
    private suspend fun processPending(): Boolean {
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

    /** Translates a typed name (Chinese, English or Latin) into the full set of names. */
    suspend fun lookupName(query: String): RecognitionResult {
        val apiKey = settings.apiKey.value
        if (apiKey.isNullOrBlank()) throw RecognitionException("设置 DeepSeek API Key 后可以自动翻译", retryable = false)
        return recognizer.lookup(apiKey, query)
    }

    /** After the user picks another name, fills in its scientific name and blurb in the background. */
    fun fillDetails(id: String) {
        scope.launch {
            runCatching {
                val before = repository.get(id) ?: return@launch
                val name = before.nameZh ?: return@launch
                val result = lookupName(name)
                val latest = repository.get(id) ?: return@launch
                if (latest.nameZh != name) return@launch
                repository.update(
                    latest.copy(
                        nameSci = result.nameSci,
                        category = result.category.name,
                        description = result.description ?: latest.description,
                    ),
                )
            }
        }
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
