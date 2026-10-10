package io.github.wgd753.naturecollect.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Turns coordinates into a readable place such as "杭州市 · 西湖区 · 西湖公园".
 * Tries the phone's own geocoder first (absent on most phones without Google
 * services), then OpenStreetMap's Nominatim.
 */
class PlaceResolver(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        fromGeocoder(latitude, longitude) ?: fromNominatim(latitude, longitude)
    }

    @Suppress("DEPRECATION")
    private fun fromGeocoder(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val address = Geocoder(context, Locale.SIMPLIFIED_CHINESE).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            ?: return null
        join(
            address.locality ?: address.adminArea,
            address.subLocality,
            address.featureName?.takeIf { it != address.subThoroughfare && it.any { c -> !c.isDigit() } },
        )
    }.getOrNull()

    private fun fromNominatim(latitude: Double, longitude: Double): String? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&accept-language=zh-CN" +
            "&lat=$latitude&lon=$longitude"
        val request = Request.Builder()
            .url(url)
            // Nominatim's usage policy asks every app to identify itself.
            .header("User-Agent", "NatureCollect/0.1 (personal Android app; github.com/wgd753/nature-collect)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val address = json.optJSONObject("address") ?: return null
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull { k -> address.optString(k).takeIf { it.isNotBlank() } }
            val city = field("city", "town", "county", "state")
            val district = field("city_district", "district", "suburb", "village")
            val spot = json.optString("name").takeIf { it.isNotBlank() }
                ?: field("park", "attraction", "leisure", "neighbourhood", "road")
            join(city, district, spot)
        }
    }.getOrNull()

    private fun join(vararg parts: String?): String? =
        parts.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ").ifEmpty { null }
}
