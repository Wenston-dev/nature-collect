package io.github.wgd753.naturecollect.ui.camera

import android.Manifest
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.withFrameNanos
import android.provider.MediaStore
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ZoomState
import androidx.lifecycle.Observer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.ml.PromptPoint
import io.github.wgd753.naturecollect.ui.PillButton
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.cutout.CutoutEditor
import io.github.wgd753.naturecollect.ui.cutout.rememberCutoutState
import io.github.wgd753.naturecollect.ml.SamPrompt
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.PaperDeep
import io.github.wgd753.naturecollect.ui.theme.Night
import io.github.wgd753.naturecollect.ui.theme.Sun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import io.github.wgd753.naturecollect.ui.tr

@Composable
fun CameraScreen(onOpenCapture: (String) -> Unit) {
    val container = rememberContainer()
    val vm: CameraViewModel = viewModel { CameraViewModel(container) }
    val stage by vm.stage.collectAsState()

    // Load the cut-out model only once the camera is actually opened, so app start stays light.
    LaunchedEffect(Unit) { runCatching { container.segmenter.warmUp() } }

    // Back steps out of the current photo instead of leaving the camera.
    BackHandler(enabled = stage != CameraStage.Live) { vm.reset() }
    // Coming back to the camera later should show the viewfinder, not an old result card.
    val latestStage by rememberUpdatedState(stage)
    DisposableEffect(Unit) {
        onDispose { if (latestStage is CameraStage.Saved || latestStage is CameraStage.Failed) vm.reset() }
    }

    AnimatedContent(
        targetState = stage,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
        label = "cameraStage",
    ) { current ->
        when (current) {
            CameraStage.Live -> LiveCamera(vm)
            is CameraStage.Finding -> FindingSubject(current.photo)
            is CameraStage.Sticker -> StickerReveal(current, vm)
            is CameraStage.Selecting -> SelectSubject(current, vm)
            is CameraStage.Saved -> SavedCard(current.captureId, vm, onOpenCapture)
            is CameraStage.Failed -> FailedMessage(current.message, vm::reset)
        }
    }
}

@Composable
private fun LiveCamera(vm: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCamera = result[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (!hasCamera) add(Manifest.permission.CAMERA)
            if (!container.location.hasPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!container.location.hasMediaLocationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    // Open the gallery app via ACTION_PICK on MediaStore: it returns a media URI,
    // which (with ACCESS_MEDIA_LOCATION) still yields the original GPS tags.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
                val (latLng, time) = withContext(Dispatchers.IO) { container.location.readExif(uri) }
                if (bitmap == null) {
                    vm.fail(tr("这张图片打不开", "Can't open this image"))
                } else {
                    vm.onPhoto(bitmap, latLng, time, imported = true)
                    val found = listOfNotNull(time?.let { tr("拍摄时间", "time") }, latLng?.let { tr("地点", "location") })
                    Toast.makeText(
                        context,
                        if (found.isEmpty()) tr("这张照片里没有时间和位置信息", "No time or location in this photo") else tr("已从照片读取${found.joinToString("和")}", "Read ${found.joinToString(" and ")} from photo"),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val flash = remember { Animatable(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    // Follow the camera's zoom so the 0.6x / 1x / 2x buttons and pinching stay in sync.
    DisposableEffect(camera) {
        val zoomState = camera?.cameraInfo?.zoomState
        val observer = Observer<ZoomState> {
            zoomRatio = it.zoomRatio
            minZoom = it.minZoomRatio
            maxZoom = it.maxZoomRatio
        }
        zoomState?.observe(lifecycleOwner, observer)
        onDispose { zoomState?.removeObserver(observer) }
    }

    if (hasCamera) {
        DisposableEffect(lensFacing) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                runCatching {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Sun))
            Spacer(Modifier.width(8.dp))
            Text(
                text = tr("对准它，让它待在框中间", "Keep it centered in the frame"),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                // Pinch to zoom, tap to focus.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(camera) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    camera?.cameraControl?.setZoomRatio((zoomRatio * zoom).coerceIn(minZoom, maxZoom))
                                }
                            }
                        }
                        .pointerInput(camera) {
                            detectTapGestures { offset ->
                                val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                                focusPoint = offset
                            }
                        },
                )
                focusPoint?.let { FocusRing(it) { focusPoint = null } }
                ZoomPresets(
                    current = zoomRatio,
                    min = minZoom,
                    max = maxZoom,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    onSelect = { camera?.cameraControl?.setZoomRatio(it) },
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("需要相机权限才能拍照", "Camera access is needed"), color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text(tr("允许使用相机", "Allow camera")) }
                }
            }
            ViewfinderCorners(Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(flash.value)
                    .background(Color.White),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(Icons.Outlined.PhotoLibrary, tr("相册", "Gallery")) {
                picker.launch(
                    Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"),
                )
            }
            ShutterButton(enabled = hasCamera) {
                scope.launch {
                    flash.snapTo(0.9f)
                    flash.animateTo(0f, tween(350))
                }
                val front = lensFacing == CameraSelector.LENS_FACING_FRONT
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toUprightBitmap(front)
                            image.close()
                            vm.onPhoto(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            vm.fail(tr("拍照失败：${exception.message}", "Capture failed: ${exception.message}"))
                        }
                    },
                )
            }
            SideButton(Icons.Outlined.Cameraswitch, tr("翻转", "Flip")) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }
        }
    }
}

private fun ImageProxy.toUprightBitmap(mirror: Boolean): Bitmap {
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    return limit(transform(toBitmap(), matrix), 2048)
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .border(4.dp, Sun, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
            .clickable(enabled = enabled) {
                scope.launch {
                    press.animateTo(0.86f, tween(70))
                    press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Sun))
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

/** White L-shaped brackets in the four corners of the viewfinder. */
@Composable
private fun ViewfinderCorners(modifier: Modifier) {
    Box(
        modifier.drawWithContent {
            val inset = 20.dp.toPx()
            val len = 28.dp.toPx()
            val stroke = 3.dp.toPx()
            val corners = listOf(
                Offset(inset, inset) to Offset(1f, 1f),
                Offset(size.width - inset, inset) to Offset(-1f, 1f),
                Offset(inset, size.height - inset) to Offset(1f, -1f),
                Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
            )
            for ((corner, dir) in corners) {
                drawLine(Color.White, corner, corner + Offset(len * dir.x, 0f), stroke, StrokeCap.Round)
                drawLine(Color.White, corner, corner + Offset(0f, len * dir.y), stroke, StrokeCap.Round)
            }
        },
    )
}

private class Sparkle(val x: Float, val y: Float, val radius: Float, val phase: Float, val speed: Float, val color: Color)

private val AuroraColors = listOf(
    Color(0xFF7FE0B5), Color(0xFF7CC8FF), Color(0xFFB59CFF), Color(0xFFF0775A), Color(0xFFF5B83D), Color(0xFF7FE0B5),
)

/**
 * While SAM encodes the photo: an aurora scan line combs over it, lighting up a
 * dot grid as it passes, stars twinkle and drift, and a rainbow rim flows
 * around the frame. Everything runs off one frame clock.
 */
@Composable
private fun FindingSubject(photo: Bitmap) {
    val sparkles = remember(photo) {
        val rnd = kotlin.random.Random(photo.width * 31 + photo.height)
        val palette = listOf(Color.White, Color(0xFFF5B83D), Color(0xFF7FE0B5), Color(0xFFB59CFF), Color(0xFFFFD6E0))
        List(30) {
            Sparkle(rnd.nextFloat(), rnd.nextFloat(), 4f + rnd.nextFloat() * 9f, rnd.nextFloat(), 0.35f + rnd.nextFloat() * 0.7f, palette[rnd.nextInt(palette.size)])
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer {
                    val s = 0.92f + 0.08f * intro.value
                    scaleX = s
                    scaleY = s
                    alpha = intro.value
                }
                .drawWithContent {
                    drawContent()
                    // Flowing rainbow rim with a soft glow built from stacked strokes.
                    val a = time * 1.4f
                    val r = size.maxDimension
                    val c = Offset(size.width / 2, size.height / 2)
                    val dir = Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * r
                    val brush = Brush.linearGradient(AuroraColors, start = c - dir, end = c + dir)
                    val corner = CornerRadius(28.dp.toPx())
                    listOf(18f to 0.08f, 10f to 0.16f, 5f to 0.35f, 2.5f to 1f).forEach { (w, alpha) ->
                        drawRoundRect(brush, cornerRadius = corner, style = Stroke(w.dp.toPx()), alpha = alpha * intro.value)
                    }
                },
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .drawWithContent {
                        drawContent()
                        val t = time
                        val w = size.width
                        val h = size.height
                        drawRect(Color(0xFF0B1A14).copy(alpha = 0.22f + 0.06f * kotlin.math.sin(t * 2f)))

                        val p = kotlin.math.sin(t * 1.5f) * 0.5f + 0.5f
                        val y = p * h
                        val band = 46.dp.toPx()
                        val hue = AuroraColors[((t * 0.6f).toInt()) % (AuroraColors.size - 1)]

                        // Dot grid that lights up near the scan line.
                        val step = 18.dp.toPx()
                        var gy = step / 2
                        while (gy < h) {
                            val d = kotlin.math.abs(gy - y)
                            if (d < band * 2.2f) {
                                val k = 1f - d / (band * 2.2f)
                                var gx = step / 2
                                while (gx < w) {
                                    drawCircle(Color.White, radius = 1.4.dp.toPx() * (0.6f + k), center = Offset(gx, gy), alpha = k * 0.75f)
                                    gx += step
                                }
                            }
                            gy += step
                        }
                        // Aurora haze and the bright line itself.
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, hue.copy(alpha = 0.38f), Color.Transparent), startY = y - band * 2, endY = y + band * 2),
                        )
                        val lineBrush = Brush.horizontalGradient(AuroraColors)
                        drawRect(lineBrush, topLeft = Offset(0f, y - 7.dp.toPx()), size = Size(w, 14.dp.toPx()), alpha = 0.25f)
                        drawRect(lineBrush, topLeft = Offset(0f, y - 1.5.dp.toPx()), size = Size(w, 3.dp.toPx()))
                        drawRect(Color.White, topLeft = Offset(0f, y - 0.5.dp.toPx()), size = Size(w, 1.dp.toPx()), alpha = 0.9f)

                        // Twinkling four-point stars drifting upward.
                        sparkles.forEach { sp ->
                            val life = (t * sp.speed + sp.phase) % 1f
                            val alpha = kotlin.math.sin(life * kotlin.math.PI.toFloat())
                            val rad = sp.radius.dp.toPx() * (0.5f + 0.5f * alpha)
                            val center = Offset(sp.x * w, sp.y * h - life * 60.dp.toPx())
                            drawCircle(sp.color, radius = rad * 1.3f, center = center, alpha = alpha * 0.18f)
                            val q = rad * 0.22f
                            val star = Path().apply {
                                moveTo(center.x, center.y - rad)
                                lineTo(center.x + q, center.y - q)
                                lineTo(center.x + rad, center.y)
                                lineTo(center.x + q, center.y + q)
                                lineTo(center.x, center.y + rad)
                                lineTo(center.x - q, center.y + q)
                                lineTo(center.x - rad, center.y)
                                lineTo(center.x - q, center.y - q)
                                close()
                            }
                            drawPath(star, sp.color, alpha = alpha)
                        }
                    },
            ) {
                Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
            }
        }
        HintChip(tr("寻找主体", "Finding subject"), Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), icon = Icons.Outlined.AutoAwesome)
    }
}

@Composable
private fun HintChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC2B2A28))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The sticker pops out over the blurred photo. Drag it up (peel it off) to
 * save; let go early and it springs back into place.
 */
@Composable
private fun StickerReveal(stage: CameraStage.Sticker, vm: CameraViewModel) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pop = remember { Animatable(0.3f) }
    val lift = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fade = remember { Animatable(1f) }
    var flying by remember { mutableStateOf(false) }

    LaunchedEffect(stage.sticker) {
        pop.snapTo(0.3f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val threshold = with(density) { 110.dp.toPx() }

        Image(
            bitmap = stage.image.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(28.dp),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Paper.copy(alpha = 0.35f)))

        Image(
            bitmap = stage.sticker.asImageBitmap(),
            contentDescription = tr("贴纸", "Sticker"),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .aspectRatio(stage.sticker.width.toFloat() / stage.sticker.height)
                .graphicsLayer {
                    val s = pop.value * lift.value
                    scaleX = s
                    scaleY = s
                    translationX = offset.value.x
                    translationY = offset.value.y
                    // Tilt with the drag, like paper peeling off at an angle.
                    rotationZ = offset.value.x / 22f
                    rotationX = (-offset.value.y / 30f).coerceIn(-18f, 18f)
                    alpha = fade.value
                    cameraDistance = 12f * density.density
                }
                .holographic(intensity = 0.45f, trigger = lift.value > 1f)
                .pointerInputPeel(
                    enabled = !flying,
                    onStart = { scope.launch { lift.animateTo(1.08f, spring(stiffness = Spring.StiffnessMedium)) } },
                    onDrag = { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                    onEnd = {
                        val o = offset.value
                        if (-o.y > threshold || hypot(o.x, o.y) > threshold * 1.6f) {
                            flying = true
                            scope.launch {
                                coroutineScope {
                                    launch { offset.animateTo(Offset(o.x * 1.5f, -heightPx), tween(420)) }
                                    launch { lift.animateTo(0.35f, tween(420)) }
                                    launch { fade.animateTo(0f, tween(420)) }
                                }
                                vm.save()
                            }
                        } else {
                            scope.launch {
                                coroutineScope {
                                    launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.5f)) }
                                    launch { lift.animateTo(1f, spring(dampingRatio = 0.5f)) }
                                }
                            }
                        }
                    },
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HintChip(tr("用手指把贴纸往上揭下来，就能保存", "Peel the sticker up to save"), icon = Icons.Outlined.TouchApp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(tr("重拍", "Retake"), onClick = vm::reset)
                PillButton(tr("圈选主体", "Select subject"), onClick = vm::startSelecting)
                PillButton(tr("直接保存", "Save as is"), primary = true) {
                    if (!flying) {
                        flying = true
                        scope.launch {
                            coroutineScope {
                                launch { offset.animateTo(Offset(0f, -heightPx), tween(450)) }
                                launch { fade.animateTo(0f, tween(450)) }
                            }
                            vm.save()
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.pointerInputPeel(
    enabled: Boolean,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = if (!enabled) this else this.then(
    Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = onEnd,
            onDragCancel = onEnd,
            onDrag = { change, amount ->
                change.consume()
                onDrag(amount)
            },
        )
    },
)

/** Circle (or tap) the object you want; the highlight shows what will be cut out. */
@Composable
private fun SelectSubject(stage: CameraStage.Selecting, vm: CameraViewModel) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val state = rememberCutoutState(
        stage.image,
        container.segmenter,
        SamPrompt.Points(listOf(PromptPoint(stage.image.bitmap.width / 2f, stage.image.bitmap.height / 2f))),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CutoutEditor(state)
        Row(
            Modifier.padding(top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(tr("重拍", "Retake"), onClick = vm::reset)
            PillButton(tr("就它了", "Use this"), primary = true, enabled = state.hasSelection) {
                scope.launch { state.sticker()?.let(vm::confirmSelection) }
            }
        }
    }
}

/** "Saved to your collection", then the recognised name fades in. */
@Composable
private fun SavedCard(captureId: String, vm: CameraViewModel, onOpenCapture: (String) -> Unit) {
    val capture by remember(captureId) { vm.observeCapture(captureId) }.collectAsState(initial = null)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }

    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 0.8f + 0.2f * enter.value
                    scaleY = 0.8f + 0.2f * enter.value
                    alpha = enter.value
                    translationY = (1f - enter.value) * 120f
                }
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Card)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            capture?.let { c ->
                StickerImage(c, Modifier.size(180.dp).holographic(0.3f, 3200))
            }
            Spacer(Modifier.height(16.dp))
            Text(tr("已收入图鉴", "Added to collection"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            val c = capture
            val waiting = c == null || c.status == RecognitionStatus.PENDING || c.status == RecognitionStatus.RUNNING
            if (waiting) {
                val error = c?.errorMessage
                if (error != null) {
                    Text(
                        tr("$error，联网或设置好后会自动识别", "$error. Will identify once online or set up"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("正在识别…", "Identifying…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (c != null && c.status == RecognitionStatus.FAILED) {
                Text(
                    c.errorMessage ?: tr("没认出来，可以在详情里手动填名字", "Not recognized. Add a name in details"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (c != null) {
                val reveal = remember { Animatable(0f) }
                LaunchedEffect(Unit) { reveal.animateTo(1f, tween(500)) }
                Column(Modifier.alpha(reveal.value), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.displayName, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    val category = Category.fromKey(c.category)
                    Text(
                        "${category.emoji} ${category.display}" + (c.nameSci?.let { " · $it" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    c.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onOpenCapture(captureId) }) { Text(tr("查看详情", "View details")) }
                Button(
                    onClick = vm::reset,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(tr("继续拍", "Keep shooting")) }
            }
        }
    }
}

@Composable
private fun FailedMessage(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("😵", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(tr("再试一次", "Try again")) }
    }
}

/**
 * Quick zoom steps. Below 1x CameraX switches to the ultra-wide lens and at
 * higher ratios to the telephoto, on phones that expose those lenses to apps.
 */
@Composable
private fun ZoomPresets(current: Float, min: Float, max: Float, modifier: Modifier = Modifier, onSelect: (Float) -> Unit) {
    if (max <= 1.01f && min >= 0.99f) return
    val presets = buildList {
        if (min < 0.95f) add(min.coerceAtLeast(0.5f))
        add(1f)
        if (max >= 2f) add(2f)
        if (max >= 5f) add(5f)
    }
    val active = presets.minByOrNull { kotlin.math.abs(it - current) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val selected = preset == active
            val size by animateDpAsState(if (selected) 40.dp else 32.dp, label = "zoomChip")
            val label = if (selected && kotlin.math.abs(current - preset) > 0.05f) {
                formatZoom(current)
            } else {
                formatZoom(preset)
            }
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f))
                    .clickable { onSelect(preset) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun formatZoom(ratio: Float): String {
    val rounded = (ratio * 10).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}

/** A ring that shrinks onto the tapped spot, then fades. */
@Composable
private fun FocusRing(at: Offset, onDone: () -> Unit) {
    val progress = remember(at) { Animatable(0f) }
    LaunchedEffect(at) {
        progress.animateTo(1f, tween(650))
        onDone()
    }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val radius = with(density) { (34 - 10 * progress.value).dp.toPx() }
                drawCircle(
                    Color.White.copy(alpha = 1f - progress.value * 0.7f),
                    radius = radius,
                    center = at,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 2.dp.toPx() }),
                )
            },
    )
}
