package io.github.wgd753.naturecollect.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.data.Settings
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.TitleFont
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCategories: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedKey by container.settings.apiKey.collectAsState()
    var key by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }
    var reveal by remember { mutableStateOf(false) }
    val savedBaseUrl by container.settings.baseUrl.collectAsState()
    val savedModel by container.settings.model.collectAsState()
    var baseUrl by remember(savedBaseUrl) { mutableStateOf(if (savedBaseUrl == Settings.DEFAULT_BASE_URL) "" else savedBaseUrl) }
    var model by remember(savedModel) { mutableStateOf(if (savedModel == Settings.DEFAULT_MODEL) "" else savedModel) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching { container.backup.export(uri) }
                    .fold({ tr("已导出 $it 条收集", "Exported $it finds") }, { tr("导出失败：", "Export failed: ") + it.message })
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching { container.backup.import(uri) }
                    .fold({ tr("已导入 $it 条收集", "Imported $it finds") }, { tr("导入失败：", "Import failed: ") + it.message })
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                container.recognition.schedule()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = MaterialTheme.colorScheme.primary)
            }
            Text(tr("设置", "Settings"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Section(tr("物种识别", "Identification")) {
                Text(
                    tr(
                        "识别使用 DeepSeek V4.1 Flash。到 platform.deepseek.com 创建 API Key 后填在这里，Key 会加密保存在手机上。",
                        "Uses DeepSeek V4.1 Flash. Create an API key at platform.deepseek.com and paste it here. It's stored encrypted on this phone.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = tr("显示", "Show"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text(Settings.DEFAULT_BASE_URL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(tr("模型", "Model")) },
                    placeholder = { Text(Settings.DEFAULT_MODEL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                val customized = baseUrl.trim().let { it.isNotEmpty() && it != Settings.DEFAULT_BASE_URL } ||
                    model.trim().let { it.isNotEmpty() && it != Settings.DEFAULT_MODEL }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("任意 OpenAI 兼容接口", "Any OpenAI-compatible API"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (customized) {
                        TextButton(onClick = {
                            baseUrl = ""
                            model = ""
                            container.settings.setBaseUrl("")
                            container.settings.setModel("")
                        }) { Text(tr("恢复默认", "Reset")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(colors = inkButton(), onClick = {
                        container.settings.setApiKey(key)
                        container.settings.setBaseUrl(baseUrl)
                        container.settings.setModel(model)
                        scope.launch {
                            // Anything that was waiting for a key gets another go.
                            container.captures.unrecognized().forEach { container.captures.markPending(it.id) }
                            container.recognition.schedule()
                        }
                        Toast.makeText(context, if (key.isBlank()) tr("已清除", "Cleared") else tr("已保存，开始识别待识别的收集", "Saved. Identifying pending finds"), Toast.LENGTH_SHORT).show()
                    }) { Text(tr("保存", "Save")) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            container.captures.unrecognized().forEach { container.captures.markPending(it.id) }
                            container.recognition.schedule()
                            Toast.makeText(context, tr("已重新排队识别", "Queued for retry"), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(tr("重试失败的识别", "Retry failed")) }
                }
            }

            Section(tr("图鉴分类", "Categories")) {
                Text(
                    tr("添加或修改分类（emoji + 名字），拍照识别时 AI 会从这些分类里挑。", "Add or edit categories (emoji + name). AI picks from these when identifying."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenCategories) { Text(tr("管理分类", "Manage categories")) }
            }

            Section(tr("地图底图", "Map source")) {
                val source by container.settings.mapSource.collectAsState()
                Text(
                    tr("高德底图在国内加载快；OpenStreetMap 适合在国外用。", "Amap is fast in mainland China; OpenStreetMap works better abroad."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(tr("高德（国内）", "Amap (China)"), selected = source == Settings.MAP_AMAP) { container.settings.setMapSource(Settings.MAP_AMAP) }
                    FilterPill("OpenStreetMap", selected = source == Settings.MAP_OSM) { container.settings.setMapSource(Settings.MAP_OSM) }
                }
            }

            Section(tr("语言", "Language")) {
                val language by container.settings.language.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("中文", selected = language == Settings.LANG_ZH) { container.settings.setLanguage(Settings.LANG_ZH) }
                    FilterPill("English", selected = language == Settings.LANG_EN) { container.settings.setLanguage(Settings.LANG_EN) }
                }
            }

            Section(tr("备份", "Backup")) {
                Text(
                    tr("所有收集、贴纸和画板打包成一个 zip。换手机时导出，再在新手机上导入。", "Packs all finds, stickers and boards into one zip. Export it, then import on your new phone."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(colors = inkButton(), onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch(tr("拾野备份-$stamp.zip", "Shiye-backup-$stamp.zip"))
                    }) { Text(tr("导出备份", "Export")) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text(tr("导入备份", "Import"))
                    }
                }
            }

            Section(tr("关于", "About")) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(io.github.wgd753.naturecollect.R.drawable.art_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr("拾野", "Shiye"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "v" + runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    tr(
                        "抠图：MobileSAM（在手机本地运行，不上传照片）\n识别：DeepSeek V4.1 Flash（只上传抠好的贴纸）\n地图：MapLibre + OpenFreeMap（OpenStreetMap 数据）",
                        "Cutout: MobileSAM (runs on device, photos stay local)\nIdentification: DeepSeek V4.1 Flash (only the sticker is uploaded)\nMap: MapLibre + OpenFreeMap (OpenStreetMap data)",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    // No cards: sections sit on the paper, separated by a faint ink hairline.
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont), color = Forest)
            content()
        }
    }
}

@Composable
private fun inkButton(): ButtonColors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
