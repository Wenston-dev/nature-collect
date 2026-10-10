package io.github.wgd753.naturecollect.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wgd753.naturecollect.recognition.RecognitionException
import io.github.wgd753.naturecollect.ui.PillButton
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.ForestDeep
import io.github.wgd753.naturecollect.ui.theme.Sun
import kotlinx.coroutines.launch

private const val DEEPSEEK_KEYS_URL = "https://platform.deepseek.com/api_keys"

/** First-run walkthrough: what the app does, then getting a DeepSeek API key in. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    fun go(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Forest, ForestDeep)))
                .systemBarsPadding()
                .imePadding(),
        ) {
            HorizontalPager(pager, modifier = Modifier.weight(1f), userScrollEnabled = true) { page ->
                when (page) {
                    0 -> WelcomePage(onNext = { go(1) })
                    1 -> ApiKeyPage(onNext = { go(2) })
                    else -> ReadyPage(onDone)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    val selected = pager.currentPage == i
                    val width by animateDpAsState(if (selected) 22.dp else 8.dp, spring(), label = "dot")
                    val color by animateColorAsState(if (selected) Sun else Color.White.copy(alpha = 0.3f), label = "dotColor")
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🐦 🌸 🍄 🐞", fontSize = 40.sp)
        Spacer(Modifier.height(20.dp))
        Text("把遇见的自然\n收进口袋", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp)
        Spacer(Modifier.height(24.dp))
        Feature("📸", "拍一张", "自动把主体抠成贴纸，不满意就用手指圈一下")
        Feature("🔍", "认出它", "AI 告诉你它叫什么、属于哪一类")
        Feature("🗺️", "记下来", "时间和地点自动记录，在图鉴、日历和地图里回看")
        Spacer(Modifier.height(32.dp))
        PillButton("开始设置", primary = false, onClick = onNext)
    }
}

@Composable
private fun Feature(emoji: String, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Sun, fontWeight = FontWeight.Bold)
            Text(body, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ApiKeyPage(onNext: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(container.settings.apiKey.value.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun verify() {
        checking = true
        result = null
        scope.launch {
            container.settings.setApiKey(key)
            result = try {
                val r = container.recognition.lookupName("麻雀")
                true to "可以用了！试着识别了一下「${r.nameZh}」"
            } catch (e: RecognitionException) {
                if (!e.retryable) container.settings.setApiKey(null)
                false to (e.message ?: "验证失败")
            } catch (e: Exception) {
                false to "验证失败：${e.message ?: e.javaClass.simpleName}"
            }
            checking = false
            if (result?.first == true) container.recognition.schedule()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("接上 AI 识别", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            "认物种用的是 DeepSeek，需要你自己的 API Key。按用量付费，识别一张只花很少的钱。Key 只加密保存在这台手机上。",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        Step(1, "点下面的按钮打开 DeepSeek 开放平台，用手机号注册登录")
        Step(2, "在「充值」里充一点余额")
        Step(3, "进入「API Keys」，点「创建 API Key」，复制下来")
        Step(4, "回到这里粘贴，点「验证并保存」")
        Spacer(Modifier.height(12.dp))
        PillButton("打开 DeepSeek 开放平台") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEEPSEEK_KEYS_URL))) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                modifier = Modifier.weight(1f),
                placeholder = { Text("sk-…", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            )
            TextButton(onClick = {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { key = it }
            }) { Text("粘贴", color = Sun) }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(targetState = result, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "verify") { r ->
            if (r != null) {
                Text(
                    (if (r.first) "✓ " else "✗ ") + r.second,
                    color = if (r.first) Sun else Color(0xFFFFB4A8),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result?.first == true) {
                PillButton("下一步", primary = false, onClick = onNext)
            } else if (checking) {
                CircularProgressIndicator(color = Sun, modifier = Modifier.size(28.dp))
            } else {
                PillButton("验证并保存", primary = false, enabled = key.length > 10) { verify() }
            }
            TextButton(onClick = onNext) { Text("以后再说", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Sun), contentAlignment = Alignment.Center) {
            Text("$number", color = ForestDeep, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ReadyPage(onDone: () -> Unit) {
    val container = rememberContainer()
    val hasKey = container.settings.apiKey.value != null
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌿", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("准备好了", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasKey) {
                "点底部中间的黄色按钮拍下第一样东西吧。\n从相册导入的照片会自动读取拍摄时间和地点。"
            } else {
                "没填 API Key 也能拍照和抠贴纸，只是不会自动识别。\n随时可以在「图鉴 → 设置」里补上。"
            },
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))
        PillButton("开始收集", primary = false, onClick = onDone)
    }
}
