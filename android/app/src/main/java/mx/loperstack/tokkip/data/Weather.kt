// The weather where you are, for Tokkip's car screen. Open-Meteo: free, no
// account or key. Only the approximate location goes out (rounded to about a
// kilometre), and only every 20 minutes while the car screen shows.

package mx.loperstack.tokkip.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.json.JSONObject

object Weather {
    data class Now(val temp: Int, val code: Int, val day: Boolean, val max: Int, val min: Int)

    @Volatile var now: Now? = null
        private set

    @Volatile private var busy = false
    @Volatile private var lastTry = 0L
    private val pool = Executors.newSingleThreadExecutor()

    fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Fetches again when it's been 20 minutes (or now, with force). Cheap to call every frame. */
    fun refresh(context: Context, force: Boolean = false) {
        val t = System.currentTimeMillis()
        if (busy || (!force && t - lastTry < 20 * 60_000L)) return
        lastTry = t
        if (!granted(context)) return
        busy = true
        locate(context.applicationContext) { loc ->
            if (loc == null) {
                busy = false
                lastTry = t - 18 * 60_000L // no fix yet: try again in two minutes
                return@locate
            }
            pool.execute {
                fetch(loc.latitude, loc.longitude)?.let { now = it }
                busy = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun locate(context: Context, done: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java) ?: return done(null)
        val last = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return done(last)
        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.FUSED_PROVIDER
        }
        runCatching {
            lm.getCurrentLocation(provider, null, context.mainExecutor) { done(it ?: last) }
        }.onFailure { done(last) }
    }

    private fun fetch(lat: Double, lon: Double): Now? = runCatching {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1",
                lat, lon,
            ),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            Now(
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                day = cur.optInt("is_day", 1) == 1,
                max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** WMO weather codes, in words. */
    fun label(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Casi despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"
        in 61..67, in 80..82 -> "Lluvia"
        in 71..77, 85, 86 -> "Nieve"
        in 95..99 -> "Tormenta"
        else -> "Nublado"
    }
}
