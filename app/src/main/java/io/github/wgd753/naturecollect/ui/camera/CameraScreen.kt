package io.github.wgd753.naturecollect.ui.camera

import android.Manifest
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.ui.PillButton
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.cutout.CutoutEditor
import io.github.wgd753.naturecollect.ui.cutout.rememberCutoutState
import io.github.wgd753.naturecollect.ml.SamPrompt
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Celadon
import io.github.wgd753.naturecollect.ui.theme.LabelBorder
import io.github.wgd753.naturecollect.ui.theme.Divider
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.DateStamp
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.paperGrain
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.data.speciesKey
import io.github.wgd753.naturecollect.ui.theme.Night
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
            is CameraStage.Finding -> FindingSubject(vm, current.photo)
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
            Box(Modifier.size(6.dp).clip(CircleShape).background(Rust))
            Spacer(Modifier.width(8.dp))
            Text(
                text = tr("对准它，让它待在框中间", "Keep it centered in the frame"),
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.sp),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
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
                    PillButton(tr("允许使用相机", "Allow camera"), primary = true) { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
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
            .border(2.5.dp, if (enabled) Rust else Rust.copy(alpha = 0.4f), CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) Card else Card.copy(alpha = 0.3f))
            .clickable(enabled = enabled) {
                scope.launch {
                    press.animateTo(0.86f, tween(70))
                    press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(54.dp).border(0.8.dp, Rust.copy(alpha = 0.35f), CircleShape))
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        Text(label, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = Color.White.copy(alpha = 0.7f))
    }
}

/** Thin white L-shaped brackets in the four corners of the viewfinder. */
@Composable
private fun ViewfinderCorners(modifier: Modifier) {
    Box(
        modifier.drawWithContent {
            val inset = 20.dp.toPx()
            val len = 22.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val corners = listOf(
                Offset(inset, inset) to Offset(1f, 1f),
                Offset(size.width - inset, inset) to Offset(-1f, 1f),
                Offset(inset, size.height - inset) to Offset(1f, -1f),
                Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
            )
            for ((corner, dir) in corners) {
                drawLine(Color.White.copy(alpha = 0.85f), corner, corner + Offset(len * dir.x, 0f), stroke, StrokeCap.Square)
                drawLine(Color.White.copy(alpha = 0.85f), corner, corner + Offset(0f, len * dir.y), stroke, StrokeCap.Square)
            }
        },
    )
}

/** White card stock: reads as light on the dimmed photo. Rust marks the scan line and the pick. */
private val ScanTint = Card

/**
 * While SAM works: the photo dims and a single scan line combs down it,
 * lighting up the photo's own edges as it passes. When the masks come back,
 * SAM's candidate layers (smallest to largest) trace out one after another
 * and the chosen one settles, just before the sticker pops out.
 */
@Composable
private fun FindingSubject(vm: CameraViewModel, photo: Bitmap) {
    val stage by vm.stage.collectAsState()
    val finding = stage as? CameraStage.Finding
    val layers = finding?.layers.orEmpty()
    val chosen = finding?.chosen ?: -1

    val edges by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { LayerArt.edges(photo).asImageBitmap() }
    }
    val layerImages = remember(layers) { layers.map { it.asImageBitmap() } }
    val layerAlpha = remember(layers) { layers.map { Animatable(0f) } }
    val scanFade = remember { Animatable(1f) }
    LaunchedEffect(layers) {
        if (layers.isEmpty()) return@LaunchedEffect
        launch { scanFade.animateTo(0f, tween(300)) }
        layerAlpha.forEachIndexed { i, a ->
            launch {
                delay(260L * i)
                a.animateTo(1f, tween(220))
                if (i != chosen) a.animateTo(0.35f, tween(260))
            }
        }
        delay(260L * layers.size + 80)
        layerAlpha.forEachIndexed { i, a -> launch { a.animateTo(if (i == chosen) 1f else 0f, tween(300)) } }
    }

    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing)),
        label = "sweep",
    )
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer {
                    val sc = 0.96f + 0.04f * intro.value
                    scaleX = sc
                    scaleY = sc
                }
                .clip(RoundedCornerShape(2.dp))
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    drawRect(Color.Black.copy(alpha = 0.32f * intro.value))
                    val dst = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())

                    val fade = scanFade.value
                    val e = edges
                    if (fade > 0f && e != null) {
                        val y = sweep * h
                        val band = 70.dp.toPx()
                        // Edges already passed stay faintly lit; the strip right behind the line glows.
                        clipRect(top = 0f, bottom = y.coerceIn(0f, h)) {
                            drawImage(e, dstSize = dst, alpha = 0.16f * fade, colorFilter = ColorFilter.tint(ScanTint, BlendMode.SrcIn))
                        }
                        for (k in 0 until 4) {
                            val top = y - band * (k + 1) / 4f
                            clipRect(top = top.coerceIn(0f, h), bottom = (y - band * k / 4f).coerceIn(0f, h)) {
                                drawImage(e, dstSize = dst, alpha = (0.9f - k * 0.2f) * fade, colorFilter = ColorFilter.tint(ScanTint, BlendMode.SrcIn))
                            }
                        }
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, ScanTint.copy(alpha = 0.12f * fade)), startY = y - band, endY = y),
                            topLeft = Offset(0f, y - band),
                            size = Size(w, band),
                        )
                        drawRect(Rust.copy(alpha = 0.95f * fade), topLeft = Offset(0f, y - 0.75.dp.toPx()), size = Size(w, 1.5.dp.toPx()))
                    }

                    layerImages.forEachIndexed { i, img ->
                        val a = layerAlpha.getOrNull(i)?.value ?: 0f
                        if (a > 0f) {
                            val tint = if (i == chosen) Rust else ScanTint
                            drawImage(img, dstSize = dst, alpha = if (i == chosen) a else a * 0.7f, colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn))
                        }
                    }
                },
        ) {
            Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = layers.isEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            HintChip(tr("寻找主体", "Finding the subject"))
        }
    }
}

@Composable
private fun HintChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .specimenCard()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Soft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Pine, style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.5.sp))
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

    val separate = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(stage.sticker) {
        pop.snapTo(0.3f)
        separate.snapTo(0f)
        burst.snapTo(0f)
        launch { separate.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { burst.animateTo(1f, tween(750, easing = LinearOutSlowInEasing)) }
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val threshold = with(density) { 110.dp.toPx() }

        Image(
            bitmap = stage.image.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(28.dp * separate.value),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Celadon.copy(alpha = 0.6f * separate.value)))
        // One quiet burst as the sticker pops: a few small white and rust dots.
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            if (t <= 0f || t >= 1f) return@Canvas
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension * 0.28f
            for (k in 0 until 7) {
                val angle = (k / 7f) * 2f * kotlin.math.PI.toFloat() + 0.4f
                val dist = base + size.minDimension * 0.16f * t
                val p = c + Offset(kotlin.math.cos(angle), kotlin.math.sin(angle)) * dist
                val r = (if (k % 2 == 0) 3.dp else 2.dp).toPx() * (1f - t * 0.5f)
                val color = if (k % 3 == 0) Rust else Color.White
                drawCircle(color, radius = r, center = p, alpha = 1f - t)
            }
        }

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
                .holographic(intensity = 0.4f, trigger = lift.value > 1f)
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
        SamPrompt.Auto,
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

/**
 * "Saved to your collection", then the recognised name fades in. The sticker
 * is mounted on a white specimen sheet with tape and a date stamp.
 */
@Composable
private fun SavedCard(captureId: String, vm: CameraViewModel, onOpenCapture: (String) -> Unit) {
    val capture by remember(captureId) { vm.observeCapture(captureId) }.collectAsState(initial = null)
    val captureRepo = rememberContainer().captures
    val allCaptures by remember(captureRepo) { captureRepo.observeAll() }.collectAsState(initial = null)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }

    // This species' place in the collection, counted by when each species was first seen.
    val speciesNumber = remember(allCaptures, capture?.nameZh) {
        val current = capture
        val all = allCaptures
        if (current == null || all == null || current.nameZh.isNullOrBlank()) {
            null
        } else {
            all.filter { !it.nameZh.isNullOrBlank() }
                .groupBy { speciesKey(it) }
                .map { (key, list) -> key to list.minOf { it.createdAt } }
                .sortedBy { it.second }
                .map { it.first }
                .indexOf(speciesKey(current))
                .takeIf { it >= 0 }
                ?.plus(1)
        }
    }

    Box(Modifier.fillMaxSize().background(Celadon).paperGrain(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 60f
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .specimenCard()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        capture?.let { c -> StickerImage(c, Modifier.size(190.dp).holographic(0.3f, 3200)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Herbarium-style label in a thin bordered box.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, LabelBorder)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            tr("已收入图鉴", "Added to collection"),
                            style = MonoStyle,
                            color = Soft,
                        )
                        Spacer(Modifier.height(6.dp))
                        SavedLabelBody(capture, speciesNumber, allCaptures)
                    }
                }
                val showStamp = io.github.wgd753.naturecollect.data.Settings.showStamp(androidx.compose.ui.platform.LocalContext.current)
                capture?.takeIf { showStamp }?.let { c ->
                    DateStamp(c.createdAt, Modifier.align(Alignment.TopEnd), size = 60.dp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(tr("查看详情", "View details")) { onOpenCapture(captureId) }
                PillButton(tr("继续拍", "Keep shooting"), primary = true, onClick = vm::reset)
            }
        }
    }
}

/** Name, scientific name and category on the saved sheet's label, or the identifying state. */
@Composable
private fun SavedLabelBody(
    capture: io.github.wgd753.naturecollect.data.db.CaptureEntity?,
    speciesNumber: Int?,
    allCaptures: List<io.github.wgd753.naturecollect.data.db.CaptureEntity>?,
) {
    val c = capture
    val waiting = c == null || c.status == RecognitionStatus.PENDING || c.status == RecognitionStatus.RUNNING
    if (waiting) {
        val error = c?.errorMessage
        if (error != null) {
            Text(
                tr("$error，联网或设置好后会自动识别", "$error. Will identify once online or set up"),
                style = MaterialTheme.typography.bodyMedium,
                color = Soft,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Rust)
                Spacer(Modifier.width(8.dp))
                Text(tr("正在识别…", "Identifying…"), style = MaterialTheme.typography.bodyMedium, color = Soft)
            }
        }
    } else if (c != null && c.status == RecognitionStatus.FAILED) {
        Text(
            c.errorMessage ?: tr("没认出来，可以在详情里手动填名字", "Not recognized. Add a name in details"),
            style = MaterialTheme.typography.bodyMedium,
            color = Soft,
        )
    } else if (c != null) {
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(Unit) { reveal.animateTo(1f, tween(500)) }
        Column(Modifier.alpha(reveal.value)) {
            val category = Category.fromKey(c.category)
            Text(
                listOfNotNull(speciesNumber?.let { "No.%03d".format(it) }, category.display).joinToString(" · "),
                style = MonoStyle,
                color = Soft,
            )
            Text(
                c.displayName,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, letterSpacing = 1.sp),
                color = Pine,
            )
            c.nameSci?.let {
                Text(it, style = MonoStyle, color = Soft)
            }
            c.description?.let {
                Hairline(Modifier.padding(vertical = 6.dp), color = Divider)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Pine)
            }
            // A new species that crosses a category milestone gets one quiet line.
            val unlock = remember(allCaptures, c) {
                allCaptures?.let { io.github.wgd753.naturecollect.ui.achievements.tierUnlockedBy(it, c) }
            }
            unlock?.let { u ->
                val fade = remember(u) { Animatable(0f) }
                LaunchedEffect(u) { fade.animateTo(1f, tween(600)) }
                Text(
                    "${u.category.display} · " + tr("解锁 ${u.tier} 种", "${u.tier} species"),
                    modifier = Modifier.padding(top = 8.dp).alpha(fade.value),
                    style = MonoStyle,
                    color = Rust,
                )
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
        Text(message, textAlign = TextAlign.Center, color = Soft)
        Spacer(Modifier.height(20.dp))
        PillButton(tr("再试一次", "Try again"), primary = true, onClick = onRetry)
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
            .clip(RoundedCornerShape(2.dp))
            .background(Night.copy(alpha = 0.45f))
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
                    .background(if (selected) Card.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable { onSelect(preset) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Pine else Color.White,
                    style = MonoStyle,
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
