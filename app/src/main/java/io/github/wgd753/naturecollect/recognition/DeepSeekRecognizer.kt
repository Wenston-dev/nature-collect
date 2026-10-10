package io.github.wgd753.naturecollect.recognition

import android.graphics.Bitmap
import android.util.Base64
import io.github.wgd753.naturecollect.data.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Identifies the subject of a sticker with DeepSeek V4.1 Flash (vision input). */
class DeepSeekRecognizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun recognize(
        apiKey: String,
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

        val body = JSONObject()
            .put("model", MODEL)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(
                        JSONObject().put("role", "user").put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
                                .put(JSONObject().put("type", "text").put("text", "$context 请识别图中的主体。")),
                        ),
                    ),
            )

        parse(chat(apiKey, body))
    }

    /**
     * Looks up a name the user typed, in Chinese, English or Latin, and returns
     * the matching Chinese name, scientific name, category and a short blurb.
     */
    suspend fun lookup(apiKey: String, query: String): RecognitionResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", MODEL)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("temperature", 0.1)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", LOOKUP_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", query.trim())),
            )
        parse(chat(apiKey, body))
    }

    private fun chat(apiKey: String, body: JSONObject): String {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw RecognitionException("API Key 无效，请到设置里检查", retryable = false)
                    response.code == 402 -> throw RecognitionException("DeepSeek 账户余额不足", retryable = false)
                    response.code == 429 || response.code >= 500 ->
                        throw RecognitionException("DeepSeek 暂时繁忙（${response.code}）", retryable = true)
                    !response.isSuccessful -> throw RecognitionException("识别失败（${response.code}）", retryable = false)
                }
                JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            }
        } catch (e: IOException) {
            throw RecognitionException("网络连接失败", retryable = true)
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val MODEL = "deepseek-flash"

        private val SYSTEM_PROMPT = """
            你是一位熟悉中国动植物的博物学家。用户会发来一张抠好的照片贴纸，请识别其中的主体。
            只输出一个 JSON 对象，不要输出其他文字，格式如下：
            {"name_zh":"中文常用名","name_sci":"学名（拉丁文，不确定就填 null）","category":"BIRD|FLOWER|PLANT|INSECT|ANIMAL|OTHER 之一","confidence":0到1之间的数字,"description":"一句有趣的简介，不超过40字","alternatives":["其他可能的中文名，最多3个"]}
            分类规则：鸟类用 BIRD；以花为主体的用 FLOWER；树木、草、叶子、蘑菇等用 PLANT；昆虫、蜘蛛等用 INSECT；其他动物（含猫狗）用 ANIMAL；不是生物用 OTHER。
            尽量具体到种；只能确定到属或科时，name_zh 写到能确定的那一级，并降低 confidence。
        """.trimIndent()

        private val LOOKUP_PROMPT = """
            你是一位熟悉中国动植物的博物学家。用户会给出一个物种或物体的名字，可能是中文名、英文名或拉丁学名。
            请把它对应到同一个物种，只输出一个 JSON 对象，不要输出其他文字：
            {"name_zh":"中文常用名","name_sci":"拉丁学名；不是生物或没有学名时填英文名","category":"BIRD|FLOWER|PLANT|INSECT|ANIMAL|OTHER 之一","confidence":0到1之间的数字,"description":"一句有趣的简介，不超过40字","alternatives":[]}
            用户给的是中文名时，name_zh 原样保留；给的是英文或学名时，翻译成最常用的中文名。
        """.trimIndent()

        /** Pulls the JSON object out of the reply, tolerating code fences or stray text. */
        fun parse(text: String): RecognitionResult {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw RecognitionException("识别结果格式不对", retryable = true)
            val json = try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: Exception) {
                throw RecognitionException("识别结果格式不对", retryable = true)
            }
            val name = json.optString("name_zh").trim()
            if (name.isEmpty()) throw RecognitionException("没有认出来", retryable = false)
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
