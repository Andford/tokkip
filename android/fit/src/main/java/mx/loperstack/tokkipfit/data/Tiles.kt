// The map under a Cardio route, only if you turn it on (Ajustes → "Mapa bajo
// tu ruta"): OpenStreetMap's tiles for the area around the route, nothing
// else is sent (no location, no account, no key). Each tile is kept on the
// phone for 30 days, so a route you've seen opens without the network; with
// no network the route is drawn on its own. The map is © OpenStreetMap
// contributors (ODbL), credited on it.

package mx.loperstack.tokkipfit.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Tile(val z: Int, val x: Int, val y: Int)

object Tiles {
    private val mem = LruCache<Tile, ImageBitmap>(80)
    // OpenStreetMap's tile policy: few connections at a time, and say who's asking.
    private val gate = Semaphore(2)
    private const val AGENT = "TokkipFit/0.7 (app personal de Android; github.com/Andford/tokkip)"
    private const val MONTH = 30L * 24 * 60 * 60 * 1000

    fun cached(t: Tile): ImageBitmap? = mem.get(t)

    suspend fun get(context: Context, t: Tile): ImageBitmap? = mem.get(t) ?: withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "tiles/${t.z}/${t.x}/${t.y}.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MONTH
        if (!fresh) runCatching {
            gate.withPermit {
                val c = URL("https://tile.openstreetmap.org/${t.z}/${t.x}/${t.y}.png").openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", AGENT)
                c.connectTimeout = 8000; c.readTimeout = 8000
                try {
                    if (c.responseCode == 200) {
                        val bytes = c.inputStream.use { it.readBytes() }
                        file.parentFile?.mkdirs()
                        file.writeBytes(bytes)
                    }
                } finally { c.disconnect() }
            }
        }
        // A stale tile is still better than none.
        runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()?.also { mem.put(t, it) }
    }

    /** Frees the space the map takes (from Ajustes, when you turn it off). */
    fun clear(context: Context) {
        mem.evictAll()
        runCatching { File(context.cacheDir, "tiles").deleteRecursively() }
    }
}
