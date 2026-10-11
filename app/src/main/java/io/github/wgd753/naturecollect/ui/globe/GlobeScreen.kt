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
import io.github.wgd753.naturecollect.ui.theme.Celadon
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.theme.Water
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.data.speciesKey
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
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
 * GCJ-02, so pins are shifted with [ChinaCoords] to line up. Fully
 * desaturated, flattened and laid thinly over the celadon page colour, so
 * the base map reads as a pale celadon survey sheet.
 */
private val AMAP_STYLE = """
    {"version":8,
     "sources":{"base":{"type":"raster","tileSize":256,"maxzoom":18,"attribution":"© 高德地图",
       "tiles":["https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"]}},
     "layers":[{"id":"bg","type":"background","paint":{"background-color":"#E2EBE3"}},
               {"id":"base","type":"raster","source":"base","paint":{
                 "raster-saturation":-1,"raster-contrast":-0.35,"raster-brightness-min":0.2,
                 "raster-opacity":0.55}}]}
""".trimIndent()
private const val SOURCE_ID = "captures"
private const val LAYER_ID = "captures-layer"

/** Finds pinned where they were found: small numbered rust-ringed pins on a celadon base map. */
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
    var center by remember { mutableStateOf<LatLng?>(null) }
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
            m.addOnCameraIdleListener { center = m.cameraPosition.target }
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
                if (!amap) tintVectorStyle(loaded)
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
                                Expression.stop(2, 0.8f),
                                Expression.stop(12, 1.0f),
                            ),
                        ),
                    ),
                )
                style = loaded
            }
        }
    }

    // Each species' catalogue number: its place in the collection by first sighting.
    val numbers = remember(captures) { speciesNumbers(captures) }

    // Push the pins into the map whenever the data or filter changes.
    LaunchedEffect(style, shown, numbers) {
        val s = style ?: return@LaunchedEffect
        val pinIds = shown.associate { it.id to pinId(numbers[speciesKey(it)]) }
        val missing = pinIds.values.distinct().filter { s.getImage(it) == null }
        val density = context.resources.displayMetrics.density
        val mono = withContext(Dispatchers.IO) {
            runCatching { ResourcesCompat.getFont(context, R.font.dm_mono) }.getOrNull() ?: android.graphics.Typeface.MONOSPACE
        }
        val icons = withContext(Dispatchers.Default) { missing.associateWith { pinBitmap(it, density, mono) } }
        icons.forEach { (id, bitmap) -> if (s.getImage(id) == null) s.addImage(id, bitmap) }
        val features = shown.map { capture ->
            val (lat, lon) = displayCoords(capture, amap)
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("icon", pinIds.getValue(capture.id))
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
                style = MonoStyle,
                color = Soft,
            )
        }
        CategoryFilterRow(selected = filter, onSelect = { filter = it })
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .specimenCard()
                .padding(6.dp)
                .background(Celadon),
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            center?.let { c ->
                val (lat, lon) = if (amap) gcjToWgsApprox(c.latitude, c.longitude) else c.latitude to c.longitude
                Text(
                    formatCoord(lat, lon),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Card.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MonoStyle,
                    color = Pine,
                )
            }
            IconButton(
                onClick = { recenter++ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Card),
            ) {
                Icon(Icons.Outlined.CenterFocusStrong, contentDescription = tr("看全部", "Show all"), tint = Pine, modifier = Modifier.size(20.dp))
            }
            if (loadFailed) {
                Text(
                    if (amap) tr("地图加载失败，请检查网络", "Map failed to load. Check your connection.") else tr("OpenStreetMap 在国内可能连不上，可以在设置里换回高德底图", "OpenStreetMap may not load in mainland China. Switch to Amap in settings."),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .specimenCard()
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Rust,
                )
            }
            if (located.isEmpty()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Card.copy(alpha = 0.94f))) {
                    Hairline()
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mascot(R.drawable.art_mascot_map, Modifier.size(56.dp), bob = false)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr("带位置的收集会出现在这里", "Finds with a location show up here"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Soft,
                        )
                    }
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

/**
 * Recolour the OpenStreetMap vector style to match the page: celadon land,
 * #C9DCD5 water. Layer ids differ between styles, so match by type and name.
 */
private fun tintVectorStyle(style: Style) {
    val land = "#%06X".format(Celadon.toArgb() and 0xFFFFFF)
    val water = "#%06X".format(Water.toArgb() and 0xFFFFFF)
    style.layers.forEach { layer ->
        val id = layer.id.lowercase()
        runCatching {
            when {
                layer is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(land))
                layer is FillLayer && "water" in id -> layer.setProperties(PropertyFactory.fillColor(water))
                layer is LineLayer && "waterway" in id -> layer.setProperties(PropertyFactory.lineColor(water))
            }
        }
    }
}

/** Catalogue number per species key, counted by when each species was first seen. */
private fun speciesNumbers(captures: List<CaptureEntity>): Map<String, Int> =
    captures.filter { !it.nameZh.isNullOrBlank() }
        .groupBy { speciesKey(it) }
        .map { (key, list) -> key to list.minOf { it.createdAt } }
        .sortedBy { it.second }
        .mapIndexed { i, (key, _) -> key to i + 1 }
        .toMap()

private fun pinId(number: Int?): String = if (number == null) "pin-dot" else "pin-$number"

/**
 * A small white pin with a rust ring: the species' catalogue number in mono,
 * or a rust dot for finds that have no name yet.
 */
private fun pinBitmap(id: String, density: Float, mono: android.graphics.Typeface): Bitmap {
    val number = id.removePrefix("pin-").toIntOrNull()
    val label = number?.toString()
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Pine.toArgb()
        typeface = mono
        textSize = 9.5f * density
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val minSide = 20f * density
    val width = max(minSide, (label?.let { textPaint.measureText(it) } ?: 0f) + 10f * density)
    val height = minSide
    val shadow = 1.5f * density
    val bitmap = Bitmap.createBitmap((width + shadow * 2).roundToInt(), (height + shadow * 2).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val rect = android.graphics.RectF(shadow, shadow, shadow + width, shadow + height)
    val r = height / 2
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Card.toArgb()
        setShadowLayer(shadow, 0f, 0.5f * density, android.graphics.Color.argb(46, 30, 50, 40))
    }
    canvas.drawRoundRect(rect, r, r, fill)
    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Rust.toArgb()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    val inset = ring.strokeWidth / 2
    canvas.drawRoundRect(android.graphics.RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r - inset, r - inset, ring)
    if (label != null) {
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    } else {
        val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Rust.toArgb() }
        canvas.drawCircle(rect.centerX(), rect.centerY(), 3f * density, dot)
    }
    return bitmap
}

/** Inverse of the GCJ-02 shift, close enough for a 3-decimal readout. */
private fun gcjToWgsApprox(lat: Double, lon: Double): Pair<Double, Double> {
    val (sLat, sLon) = ChinaCoords.wgs84ToGcj02(lat, lon)
    return (lat * 2 - sLat) to (lon * 2 - sLon)
}

private fun formatCoord(lat: Double, lon: Double): String =
    "%.3f°%s  %.3f°%s".format(
        java.util.Locale.ENGLISH,
        kotlin.math.abs(lat), if (lat >= 0) "N" else "S",
        kotlin.math.abs(lon), if (lon >= 0) "E" else "W",
    )
