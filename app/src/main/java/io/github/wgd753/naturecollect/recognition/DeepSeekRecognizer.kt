package io.github.wgd753.naturecollect.recognition

import android.graphics.Bitmap
import android.util.Base64
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RecognitionResult(
    val nameZh: String,
    val nameSci: String?,
    val category: Category,
    val confidence: Float,
    val description: String?,
    val alternatives: List<String>,
)

class RecognitionException(message: String, val retryable: Boolean) : Exception(message)

/**
 * Identifies the subject of a sticker through any OpenAI-compatible chat
 * completions API (DeepSeek by default). Base URL and model are passed per
 * call so settings changes apply immediately.
 */
class DeepSeekRecognizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun recognize(
        apiKey: String,
        baseUrl: String,
        model: String,
        image: Bitmap,
        latitude: Double?,
        longitude: Double?,
        takenAt: Long,
    ): RecognitionResult = withContext(Dispatchers.IO) {
        val jpeg = ByteArrayOutputStream().use {
            image.compress(Bitmap.CompressFormat.JPEG, 88, it)
            it.toByteArray()
        }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

        val context = buildString {
            append("拍摄时间：").append(SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(takenAt)))
            if (latitude != null && longitude != null) {
                append("；拍摄地点约在纬度 %.2f、经度 %.2f".format(Locale.US, latitude, longitude))
            }
            append("。")
        }

        val body = requestBody(baseUrl, model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(Category.entries)))
                    .put(
                        JSONObject().put("role", "user").put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
                                .put(JSONObject().put("type", "text").put("text", "$context 请识别图中的主体。")),
                        ),
                    ),
            )

        parse(chat(apiKey, baseUrl, body))
    }

    /**
     * Looks up a name the user typed, in Chinese, English or Latin, and returns
     * the matching Chinese name, scientific name, category and a short blurb.
     */
    suspend fun lookup(apiKey: String, baseUrl: String, model: String, query: String): RecognitionResult = withContext(Dispatchers.IO) {
        val body = requestBody(baseUrl, model)
            .put("temperature", 0.1)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", lookupPrompt(Category.entries)))
                    .put(JSONObject().put("role", "user").put("content", query.trim())),
            )
        parse(chat(apiKey, baseUrl, body))
    }

    /** Model plus DeepSeek's "thinking off" switch, which other providers may reject. */
    private fun requestBody(baseUrl: String, model: String): JSONObject {
        val body = JSONObject().put("model", model.trim().ifEmpty { DEFAULT_MODEL })
        if (baseUrl.contains("deepseek.com", ignoreCase = true)) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        return body
    }

    private fun chat(apiKey: String, baseUrl: String, body: JSONObject): String {
        val url = endpoint(baseUrl).toHttpUrlOrNull()
            ?: throw RecognitionException(tr("Base URL 格式不对，请到设置里检查", "Invalid Base URL. Check Settings"), retryable = false)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw RecognitionException(tr("API Key 无效，请到设置里检查", "Invalid API key. Check Settings"), retryable = false)
                    response.code == 402 -> throw RecognitionException(tr("识别服务账户余额不足", "AI service account balance is too low"), retryable = false)
                    response.code == 404 -> throw RecognitionException(tr("接口地址或模型不对（404），请到设置里检查", "Wrong Base URL or model (404). Check Settings"), retryable = false)
                    response.code == 429 || response.code >= 500 ->
                        throw RecognitionException(tr("识别服务暂时繁忙（${response.code}）", "The AI service is busy (${response.code})"), retryable = true)
                    !response.isSuccessful -> throw RecognitionException(tr("识别失败（${response.code}）", "Identification failed (${response.code})"), retryable = false)
                }
                try {
                    JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                } catch (e: org.json.JSONException) {
                    throw RecognitionException(tr("识别服务返回的格式不对", "Unexpected reply from the AI service"), retryable = false)
                }
            }
        } catch (e: IOException) {
            throw RecognitionException(tr("网络连接失败", "Network connection failed"), retryable = true)
        }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        private const val DEFAULT_MODEL = "deepseek-flash"

        /** "<base>/chat/completions", without doubling it if the user already typed the full path. */
        fun endpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().ifEmpty { DEFAULT_BASE_URL }.trimEnd('/')
            val base = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            return if (base.endsWith("/chat/completions", ignoreCase = true)) base else "$base/chat/completions"
        }

        /** The user's categories, one per line, for the model to choose from. */
        private fun categoryList(categories: List<Category>): String =
            categories.joinToString("\n") { c ->
                "- ${c.key}：${c.emoji}${c.label}" + (c.hint?.let { "（$it）" } ?: "")
            }

        private fun systemPrompt(categories: List<Category>) = """
            你是一位熟悉中国动植物的博物学家。用户会发来一张抠好的照片贴纸，请识别其中最主要、最完整的那个主体。
            只输出一个 JSON 对象，不要输出其他文字，格式如下：
            {"name_zh":"中文常用名","name_sci":"学名（拉丁文；不是生物就填英文名；不确定就填 null）","category":"下面分类里的一个代号","confidence":0到1之间的数字,"description":"一句有趣的简介，不超过40字","alternatives":["其他可能的中文名，最多3个"]}
            可选分类（category 填冒号前的代号，选最贴切的一个；都不合适就填 ${Category.OTHER_KEY}）：
            {CATEGORIES}
            尽量具体到种；只能确定到属或科时，name_zh 写到能确定的那一级，并降低 confidence。
        """.trimIndent().replace("{CATEGORIES}", categoryList(categories))

        private fun lookupPrompt(categories: List<Category>) = """
            你是一位熟悉中国动植物的博物学家。用户会给出一个物种或物体的名字，可能是中文名、英文名或拉丁学名。
            请把它对应到同一个物种，只输出一个 JSON 对象，不要输出其他文字：
            {"name_zh":"中文常用名","name_sci":"拉丁学名；不是生物或没有学名时填英文名","category":"下面分类里的一个代号","confidence":0到1之间的数字,"description":"一句有趣的简介，不超过40字","alternatives":[]}
            可选分类（填冒号前的代号；都不合适就填 ${Category.OTHER_KEY}）：
            {CATEGORIES}
            用户给的是中文名时，name_zh 原样保留；给的是英文或学名时，翻译成最常用的中文名。
        """.trimIndent().replace("{CATEGORIES}", categoryList(categories))

        /** Pulls the JSON object out of the reply, tolerating code fences or stray text. */
        fun parse(text: String): RecognitionResult {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw RecognitionException(tr("识别结果格式不对", "Couldn't read the result"), retryable = true)
            val json = try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: Exception) {
                throw RecognitionException(tr("识别结果格式不对", "Couldn't read the result"), retryable = true)
            }
            val name = json.optString("name_zh").trim()
            if (name.isEmpty()) throw RecognitionException(tr("没有认出来", "Couldn't identify it"), retryable = false)
            val alternatives = json.optJSONArray("alternatives")?.let { arr ->
                List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotEmpty() && it != name }
            }.orEmpty()
            return RecognitionResult(
                nameZh = name,
                nameSci = json.optString("name_sci").trim().takeIf { it.isNotEmpty() && it != "null" },
                category = Category.fromKey(json.optString("category")),
                confidence = json.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                description = json.optString("description").trim().takeIf { it.isNotEmpty() && it != "null" },
                alternatives = alternatives.take(3),
            )
        }
    }
}
