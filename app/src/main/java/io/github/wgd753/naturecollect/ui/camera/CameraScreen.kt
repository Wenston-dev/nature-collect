package io.github.wgd753.naturecollect.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalDensity
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
import io.github.wgd753.naturecollect.ui.cutout.SubjectPicker
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
fun CameraScreen(onOpenCapture: (String) -> Unit) {
    val container = rememberContainer()
    val vm: CameraViewModel = viewModel { CameraViewModel(container) }
    val stage by vm.stage.collectAsState()

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
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
                val (latLng, time) = withContext(Dispatchers.IO) { container.location.readExif(uri) }
                if (bitmap != null) vm.onPhoto(bitmap, latLng, time) else vm.fail("这张图片打不开")
            }
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val flash = remember { Animatable(0f) }

    if (hasCamera) {
        DisposableEffect(lensFacing) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "拍下你遇见的自然",
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PaperDeep),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要相机权限才能拍照", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text("允许使用相机") }
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
            SideButton(Icons.Outlined.PhotoLibrary, "相册") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                            vm.fail("拍照失败：${exception.message}")
                        }
                    },
                )
            }
            SideButton(Icons.Outlined.Cameraswitch, "翻转") {
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
            .border(4.dp, Card, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            .clickable(enabled = enabled) {
                scope.launch {
                    press.animateTo(0.86f, tween(70))
                    press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = "拍照", tint = Color.White)
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

/** The photo with a light band sweeping over it while the encoder runs. */
@Composable
private fun FindingSubject(photo: Bitmap) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "sweep",
    )
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    val y = sweep * size.height
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startY = y - 120f,
                            endY = y + 120f,
                        ),
                    )
                },
        ) {
            Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
        }
        HintChip("正在找出照片里的主体…", Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
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
            contentDescription = "贴纸",
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
                .holographic(intensity = if (lift.value > 1f) 0.6f else 0.38f)
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
            HintChip("用手指把贴纸往上揭下来，就能保存", icon = Icons.Outlined.TouchApp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("重拍", onClick = vm::reset)
                PillButton("圈选主体", onClick = vm::startSelecting)
                PillButton("直接保存", primary = true) {
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "用手指圈出你想收集的东西",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "圈得松一点没关系；也可以只点一下",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SubjectPicker(stage.image.bitmap, stage.overlay, stage.busy, onPrompt = vm::select)
        }
        Row(
            Modifier.padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton("重拍", onClick = vm::reset)
            PillButton("就它了", primary = true, enabled = !stage.busy, onClick = vm::confirmSelection)
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
            Text("已收入图鉴", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            val c = capture
            val waiting = c == null || c.status == RecognitionStatus.PENDING || c.status == RecognitionStatus.RUNNING
            if (waiting) {
                val error = c?.errorMessage
                if (error != null) {
                    Text(
                        "$error，联网或设置好后会自动识别",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在识别…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (c != null && c.status == RecognitionStatus.FAILED) {
                Text(
                    c.errorMessage ?: "没认出来，可以在详情里手动填名字",
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
                        "${category.emoji} ${category.label}" + (c.nameSci?.let { " · $it" } ?: ""),
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
                OutlinedButton(onClick = { onOpenCapture(captureId) }) { Text("查看详情") }
                Button(
                    onClick = vm::reset,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("继续拍") }
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
        Button(onClick = onRetry) { Text("再试一次") }
    }
}
