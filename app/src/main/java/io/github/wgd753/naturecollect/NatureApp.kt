package io.github.wgd753.naturecollect

import android.app.Application
import io.github.wgd753.naturecollect.data.Backup
import io.github.wgd753.naturecollect.data.BoardRepository
import io.github.wgd753.naturecollect.data.CaptureRepository
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.Settings
import io.github.wgd753.naturecollect.data.db.AppDatabase
import io.github.wgd753.naturecollect.location.LocationHelper
import io.github.wgd753.naturecollect.ml.SamSegmenter
import io.github.wgd753.naturecollect.recognition.DeepSeekRecognizer
import io.github.wgd753.naturecollect.recognition.RecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/** Hand-wired dependencies; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(app)
    val media = MediaFiles(app)
    val settings = Settings(app)
    val captures = CaptureRepository(db.captureDao(), db.boardDao(), media)
    val boards = BoardRepository(db.boardDao())
    val segmenter = SamSegmenter(app)
    val location = LocationHelper(app)
    val recognition = RecognitionService(app, captures, media, settings, DeepSeekRecognizer())
    val backup = Backup(app, db, media)
}

class NatureApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
        container.appScope.launch {
            container.boards.ensureDefaultBoard()
            container.segmenter.warmUp()
        }
        // Pick up anything that was waiting for a network when the app last closed.
        container.recognition.schedule()
    }
}
