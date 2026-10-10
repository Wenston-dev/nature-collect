package io.github.wgd753.naturecollect.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

/** Location without Google Play services: the platform LocationManager plus photo EXIF. */
class LocationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix if one arrives quickly, otherwise the most recent known location. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 4000): Location? {
        if (!hasPermission()) return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val lastKnown = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || providers.isEmpty()) return lastKnown
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(providers.first(), signal, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }
        }
        return fresh ?: lastKnown
    }

    /**
     * GPS position and capture time stored in an imported photo, if any.
     *
     * Android hides photo locations from apps unless they hold
     * ACCESS_MEDIA_LOCATION and explicitly ask for the original file, so try
     * that first and fall back to the redacted copy (which still has the time).
     */
    fun readExif(uri: Uri): Pair<DoubleArray?, Long?> {
        val candidates = buildList {
            originalUri(uri)?.let(::add)
            add(uri)
        }
        var time: Long? = null
        for (candidate in candidates) {
            val result = runCatching {
                context.contentResolver.openInputStream(candidate)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    val millis = raw?.let { runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }.getOrNull() }
                    // A redacted location reads as 0,0; treat it as missing.
                    val latLong = exif.latLong?.takeIf { it[0] != 0.0 || it[1] != 0.0 }
                    latLong to millis
                }
            }.getOrNull() ?: continue
            time = time ?: result.second
            if (result.first != null) return result.first to time
        }
        return null to time
    }

    fun hasMediaLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The un-redacted media URI for a picked photo, when the system will give it to us. */
    private fun originalUri(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasMediaLocationPermission()) return null
        return runCatching {
            val media = if (uri.authority == MediaStore.AUTHORITY) uri else MediaStore.getMediaUri(context, uri)
            media?.let { MediaStore.setRequireOriginal(it) }
        }.getOrNull()
    }
}
