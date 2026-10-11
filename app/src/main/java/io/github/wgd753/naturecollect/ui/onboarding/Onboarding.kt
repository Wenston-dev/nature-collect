package io.github.wgd753.naturecollect.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import io.github.wgd753.naturecollect.ui.Mascot
import io.github.wgd753.naturecollect.R
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
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.paperGrain
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Celadon
import io.github.wgd753.naturecollect.ui.theme.Divider
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.theme.spaced
import kotlinx.coroutines.launch

private const val DEEPSEEK_KEYS_URL = "https://platform.deepseek.com/api_keys"

/**
 * First-run walkthrough: two short intro pages, the DeepSeek key, done.
 * Light and quiet: one illustration and one sentence per page, the button
 * always in the same place at the bottom.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val pages = 4
    val pager = rememberPagerState { pages }
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
                .background(Celadon)
                .paperGrain()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (pager.currentPage < 2) {
                    TextButton(onClick = { go(2) }) { Text(tr("跳过", "Skip"), color = Soft) }
                }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> IntroPage(
                        R.drawable.art_mascot_camera,
                        tr("拍下遇见的自然", "Snap the nature you meet"),
                        tr("照片里的主体会自动变成一张贴纸", "The subject turns into a sticker on its own"),
                    )
                    1 -> IntroPage(
                        R.drawable.art_mascot_map,
                        tr("它是谁，在哪遇见", "What it is, and where"),
                        tr("自动识别物种，记下时间和地点", "Species, time and place are filled in for you"),
                    )
                    2 -> ApiKeyPage(onNext = { go(3) })
                    else -> IntroPage(
                        R.drawable.art_mascot_cheer,
                        tr("准备好了", "You're all set"),
                        if (container().settings.apiKey.value != null) {
                            tr("去拍下第一样东西吧", "Go find your first one")
                        } else {
                            tr("之后可以在设置里填 API Key", "You can add an API key later in Settings")
                        },
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(pages) { i ->
                        val selected = pager.currentPage == i
                        // Short flat bars; the current page gets the rust accent.
                        val width by animateDpAsState(if (selected) 18.dp else 8.dp, spring(), label = "dot")
                        val color by animateColorAsState(if (selected) Rust else Pine.copy(alpha = 0.22f), label = "dotColor")
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(2.dp)
                                .width(width)
                                .background(color),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                // The key page has its own buttons; keep the slot so nothing jumps.
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    if (pager.currentPage != 2) {
                        val last = pager.currentPage == pages - 1
                        WideButton(if (last) tr("开始收集", "Start") else tr("下一步", "Next")) {
                            if (last) onDone() else go(pager.currentPage + 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun container() = rememberContainer()

@Composable
private fun IntroPage(art: Int, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(art, Modifier.size(220.dp))
        Spacer(Modifier.height(36.dp))
        Text(spaced(title), color = Pine, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, color = Soft, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WideButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) Pine else Pine.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Card, style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp))
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
                true to tr("已连接", "Connected")
            } catch (e: RecognitionException) {
                if (!e.retryable) container.settings.setApiKey(null)
                false to (e.message ?: tr("验证失败", "Verification failed"))
            } catch (e: Exception) {
                false to tr("验证失败：", "Verification failed: ") + (e.message ?: e.javaClass.simpleName)
            }
            checking = false
            if (result?.first == true) container.recognition.schedule()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
    ) {
        Text(spaced(tr("接上 AI 识别", "Connect AI")), color = Pine, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "识别用的是 DeepSeek，需要你自己的 API Key，只加密保存在这台手机上。",
                "Identification uses DeepSeek with your own API key, stored encrypted on this phone.",
            ),
            color = Soft,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .specimenCard()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Step(1, tr("在 DeepSeek 开放平台注册并充值少量余额", "Sign up on the DeepSeek platform and add a little balance"))
            Hairline(color = Divider)
            Step(2, tr("在「API Keys」里创建一个 Key 并复制", "Create a key under API Keys and copy it"))
            Hairline(color = Divider)
            Step(3, tr("粘贴到下面", "Paste it below"))
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEEPSEEK_KEYS_URL))) }
            }) { Text(tr("打开 DeepSeek 开放平台 ↗", "Open DeepSeek platform ↗"), color = Pine, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                modifier = Modifier.weight(1f),
                placeholder = { Text("sk-…", color = Soft) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Pine),
            )
            TextButton(onClick = {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { key = it }
            }) { Text(tr("粘贴", "Paste"), color = Pine) }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(targetState = result, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "verify") { r ->
            if (r != null) {
                Text(
                    r.second,
                    color = if (r.first) Pine else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            when {
                checking -> CircularProgressIndicator(color = Pine, modifier = Modifier.size(28.dp))
                result?.first == true -> WideButton(tr("下一步", "Next"), onClick = onNext)
                else -> WideButton(tr("验证并保存", "Verify and save"), enabled = key.length > 10) { verify() }
            }
        }
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("以后再说", "Later"), color = Soft)
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text("%02d".format(number), color = Rust, style = MonoStyle.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Pine, style = MaterialTheme.typography.bodyMedium)
    }
}
