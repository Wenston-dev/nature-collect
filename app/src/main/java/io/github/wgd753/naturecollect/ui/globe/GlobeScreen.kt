package io.github.wgd753.naturecollect.ui.globe

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import io.github.wgd753.naturecollect.ui.Mascot
import io.github.wgd753.naturecollect.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.CategoryFilterRow
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.data.Settings
import io.github.wgd753.naturecollect.location.ChinaCoords
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import io.github.wgd753.naturecollect.ui.theme.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.max
import kotlin.math.roundToInt

private const val OSM_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/**
 * 高德 raster tiles: no key, quick from mainland China. They are drawn in
 * GCJ-02, so pins are shifted with [ChinaCoords] to line up.
 */
private val AMAP_STYLE = """
    {"version":8,
     "sources":{"base":{"type":"raster","tileSize":256,"maxzoom":18,"attribution":"© 高德地图",
       "tiles":["https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"]}},
     "layers":[{"id":"bg","type":"background","paint":{"background-color":"#EEF3EC"}},
               {"id":"base","type":"raster","source":"base","paint":{"raster-saturation":-0.25}}]}
""".trimIndent()
private const val SOURCE_ID = "captures"
private const val LAYER_ID = "captures-layer"

/** Stickers pinned where they were found, on an OpenStreetMap base map. */
@Composable
fun GlobeScreen(onOpenCapture: (String) -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captures by container.captures.observeAll().collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    val located = captures.filter(::hasPlace)
    val shown = located.filter { filter == null || Category.fromKey(it.category).key == filter?.key }
    val source by container.settings.mapSource.collectAsState()
    val amap = source == Settings.MAP_AMAP
    var loadFailed by remember { mutableStateOf(false) }

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var recenter by remember { mutableIntStateOf(0) }
    val openCapture by rememberUpdatedState(onOpenCapture)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
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
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val hit = m.queryRenderedFeatures(screen, LAYER_ID).firstOrNull()
                val id = hit?.getStringProperty("cid")
                if (id != null) openCapture(id)
                id != null
            }
        }
        mapView.addOnDidFailLoadingMapListener { loadFailed = true }
        mapView.addOnDidFinishLoadingMapListener { loadFailed = false }
    }

    // (Re)load the base map whenever the tile source changes.
    LaunchedEffect(map, source) {
        val m = map ?: return@LaunchedEffect
        style = null
        loadFailed = false
        val builder = if (amap) Style.Builder().fromJson(AMAP_STYLE) else Style.Builder().fromUri(OSM_STYLE_URL)
        run {
            m.setStyle(builder) { loaded ->
                loaded.addSource(GeoJsonSource(SOURCE_ID))
                loaded.addLayer(
                    SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconSize(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(2, 0.45f),
                                Expression.stop(10, 0.75f),
                                Expression.stop(16, 1.0f),
                            ),
                        ),
                    ),
                )
                style = loaded
            }
        }
    }

    // Push the stickers into the map whenever the data or filter changes.
    LaunchedEffect(style, shown) {
        val s = style ?: return@LaunchedEffect
        val icons = withContext(Dispatchers.IO) { shown.associate { it.id to markerBitmap(container.media, it) } }
        icons.forEach { (id, bitmap) -> if (bitmap != null && s.getImage(id) == null) s.addImage(id, bitmap) }
        val features = shown.filter { icons[it.id] != null }.map { capture ->
            val (lat, lon) = displayCoords(capture, amap)
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("icon", capture.id)
                addStringProperty("cid", capture.id)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Re-fit when the map is ready, the filter changes, or the captures finish loading.
    val shownIds = remember(shown) { shown.map { it.id }.toSet() }
    LaunchedEffect(map, style, recenter, filter, shownIds) {
        val m = map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        fitTo(m, shown, amap)
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(tr("地图", "Map")) {
            Text(
                located.map { "%.3f,%.3f".format(it.latitude, it.longitude) }.distinct().size.let { tr("$it 个地点", if (it == 1) "1 place" else "$it places") },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CategoryFilterRow(selected = filter, onSelect = { filter = it })
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = { recenter++ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Card),
            ) {
                Icon(Icons.Outlined.CenterFocusStrong, contentDescription = tr("看全部", "Show all"), tint = MaterialTheme.colorScheme.primary)
            }
            if (loadFailed) {
                Text(
                    if (amap) tr("地图加载失败，请检查网络", "Map failed to load. Check your connection.") else tr("OpenStreetMap 在国内可能连不上，可以在设置里换回高德底图", "OpenStreetMap may not load in mainland China. Switch to Amap in settings."),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (located.isEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mascot(R.drawable.art_mascot_map, Modifier.size(64.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("带位置的收集会出现在这里", "Finds with a location show up here"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Only real positions go on the map. Older imports may carry 0,0 (a location
 * the system redacted), which would drag the camera out to the empty ocean.
 */
private fun hasPlace(c: CaptureEntity): Boolean {
    val lat = c.latitude ?: return false
    val lon = c.longitude ?: return false
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
    return kotlin.math.abs(lat) > 0.01 || kotlin.math.abs(lon) > 0.01
}

/** Where to draw a capture: shifted to GCJ-02 on 高德 tiles, raw GPS otherwise. */
private fun displayCoords(capture: CaptureEntity, amap: Boolean): Pair<Double, Double> {
    val lat = capture.latitude!!
    val lon = capture.longitude!!
    return if (amap) ChinaCoords.wgs84ToGcj02(lat, lon) else lat to lon
}

private fun fitTo(map: MapLibreMap, captures: List<CaptureEntity>, amap: Boolean) {
    val all = captures.filter(::hasPlace)
        .map { c -> displayCoords(c, amap).let { (lat, lon) -> LatLng(lat, lon) } }
    if (all.isEmpty()) return
    // Frame where most finds are: one photo from far away (a trip, a bad GPS tag)
    // shouldn't zoom the whole map out to an empty ocean.
    val midLat = all.map { it.latitude }.sorted()[all.size / 2]
    val midLon = all.map { it.longitude }.sorted()[all.size / 2]
    val near = all.filter { kotlin.math.abs(it.latitude - midLat) < 8 && kotlin.math.abs(it.longitude - midLon) < 8 }
    val points = near.ifEmpty { all }
    val spread = maxOf(
        points.maxOf { it.latitude } - points.minOf { it.latitude },
        points.maxOf { it.longitude } - points.minOf { it.longitude },
    )
    if (points.size == 1 || spread < 0.002) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14.0))
    } else {
        val bounds = LatLngBounds.Builder().includes(points).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
    }
}

/** A small copy of the sticker sized for the map (about 72dp tall on screen). */
private fun markerBitmap(media: MediaFiles, capture: CaptureEntity): Bitmap? {
    val file = media.file(capture.stickerFile)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 220) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = 220f / max(decoded.width, decoded.height)
    return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
}
