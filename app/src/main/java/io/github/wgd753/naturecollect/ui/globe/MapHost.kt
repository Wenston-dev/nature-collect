package io.github.wgd753.naturecollect.ui.globe

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * One MapView for the whole app, kept alive while the Map tab is closed.
 * Building and destroying a MapView on every visit to the tab crashed the app
 * when switching tabs quickly: the native renderer was still tearing down the
 * old map while the next one started up.
 */
class MapHost(context: Context) {
    val view: MapView = MapView(context).apply { onCreate(null) }
    var map by mutableStateOf<MapLibreMap?>(null)
        private set
    var style by mutableStateOf<Style?>(null)
    /** The map source [style] was built from. */
    var styleSource: String? = null
    var loadFailed by mutableStateOf(false)

    /** The screen currently showing the map; a screen leaving late must not stop the next one's map. */
    private var owner: Any? = null
    private var destroyed = false

    init {
        view.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            // Below this the 高德 raster has no tiles and the map looks empty.
            m.setMinZoomPreference(3.0)
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                .target(LatLng(34.0, 108.0))
                .zoom(3.0)
                .build()
            map = m
        }
        view.addOnDidFailLoadingMapListener { loadFailed = true }
        view.addOnDidFinishLoadingMapListener { loadFailed = false }
    }

    /** Detaches the view from whichever screen held it last, so a new screen can take it. */
    fun takeView(): MapView {
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /** Runs the map while [lifecycleOwner] is visible; returns the call that stops it. */
    fun show(lifecycleOwner: LifecycleOwner): () -> Unit {
        val token = Any()
        owner = token
        val observer = LifecycleEventObserver { _, event ->
            if (owner !== token || destroyed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val state = lifecycleOwner.lifecycle.currentState
        if (!destroyed) {
            if (state.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
            if (state.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        }
        return {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (owner === token) {
                owner = null
                if (!destroyed) {
                    view.onPause()
                    view.onStop()
                }
            }
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        owner = null
        style = null
        map = null
        view.onPause()
        view.onStop()
        view.onDestroy()
    }
}

/** The app's [MapHost], destroyed together with the activity's UI. */
@Composable
fun rememberMapHost(): MapHost {
    val context = LocalContext.current
    val host = remember(context) { MapHost(context) }
    DisposableEffect(host) { onDispose { host.destroy() } }
    return host
}
