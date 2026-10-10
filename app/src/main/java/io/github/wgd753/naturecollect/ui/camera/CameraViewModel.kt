package io.github.wgd753.naturecollect.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wgd753.naturecollect.AppContainer
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ml.PromptPoint
import io.github.wgd753.naturecollect.ml.SamImage
import io.github.wgd753.naturecollect.ml.StickerMaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

sealed interface CameraStage {
    data object Live : CameraStage

    /** Encoder running on the new photo. */
    data class Finding(val photo: Bitmap) : CameraStage

    /** A sticker is ready to be peeled off. */
    data class Sticker(val image: SamImage, val sticker: Bitmap) : CameraStage

    /** The user is circling, tapping or erasing to choose a different subject. */
    data class Selecting(val image: SamImage) : CameraStage

    data class Saved(val captureId: String) : CameraStage

    data class Failed(val message: String) : CameraStage
}

class CameraViewModel(private val container: AppContainer) : ViewModel() {
    private val _stage = MutableStateFlow<CameraStage>(CameraStage.Live)
    val stage: StateFlow<CameraStage> = _stage.asStateFlow()

    private var photo: Bitmap? = null
    private var takenAt: Long = 0
    private var latLng: DoubleArray? = null
    private var job: Job? = null

    /**
     * Starts the pipeline for a new photo. Imported photos bring their own EXIF
     * time and place; if they have no GPS we leave the place empty rather than
     * stamping today's location on an old picture.
     */
    fun onPhoto(bitmap: Bitmap, exifLatLng: DoubleArray? = null, exifTime: Long? = null, imported: Boolean = false) {
        job?.cancel()
        photo = bitmap
        takenAt = exifTime ?: System.currentTimeMillis()
        latLng = exifLatLng
        _stage.value = CameraStage.Finding(bitmap)
        job = viewModelScope.launch {
            val location = if (exifLatLng == null && !imported) async { container.location.current() } else null
            try {
                val image = container.segmenter.encode(bitmap)
                // Default prompt: whatever is in the middle of the frame.
                val alpha = container.segmenter.segment(image, listOf(PromptPoint(image.bitmap.width / 2f, image.bitmap.height / 2f)))
                val sticker = StickerMaker.make(image.bitmap, alpha)
                latLng = latLng ?: location?.await()?.let { doubleArrayOf(it.latitude, it.longitude) }
                _stage.value = if (sticker != null) {
                    CameraStage.Sticker(image, sticker)
                } else {
                    CameraStage.Selecting(image)
                }
            } catch (e: Exception) {
                _stage.value = CameraStage.Failed("抠图失败：${describe(e)}")
            }
        }
    }

    fun startSelecting() {
        val stage = _stage.value as? CameraStage.Sticker ?: return
        _stage.value = CameraStage.Selecting(stage.image)
    }

    /** Uses the sticker made in the selection editor. */
    fun confirmSelection(sticker: Bitmap) {
        val stage = _stage.value as? CameraStage.Selecting ?: return
        _stage.value = CameraStage.Sticker(stage.image, sticker)
    }

    fun save() {
        val stage = _stage.value as? CameraStage.Sticker ?: return
        val original = photo ?: return
        viewModelScope.launch {
            val capture = container.captures.create(
                photo = limit(original, 2048),
                sticker = stage.sticker,
                latitude = latLng?.get(0),
                longitude = latLng?.get(1),
                takenAt = takenAt,
            )
            _stage.value = CameraStage.Saved(capture.id)
            container.recognition.schedule()
        }
    }

    fun observeCapture(id: String): Flow<CaptureEntity?> =
        if (id.isEmpty()) emptyFlow() else container.captures.observe(id)

    fun reset() {
        job?.cancel()
        photo = null
        _stage.value = CameraStage.Live
    }

    fun fail(message: String) {
        _stage.value = CameraStage.Failed(message)
    }

    /** Exception name plus where in our code it happened, so a screenshot is enough to debug. */
    private fun describe(e: Throwable): String {
        val frame = e.stackTrace.firstOrNull { it.className.startsWith("io.github.wgd753") }
        val where = frame?.let { "\n${it.fileName}:${it.lineNumber}" }.orEmpty()
        return (e.message ?: e.javaClass.simpleName) + where
    }
}
