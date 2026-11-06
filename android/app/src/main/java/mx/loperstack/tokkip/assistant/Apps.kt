package mx.loperstack.tokkip.assistant

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tokkip opening other apps for you: music in Spotify, YouTube Music or
 * YouTube, and a search in your browser. The app itself connects to nothing
 * for these: it hands the request to the app that does it.
 */
object Apps {
    const val SPOTIFY = "com.spotify.music"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val YOUTUBE = "com.google.android.youtube"

    /** Android only lets an app open another while you're using it (not from the watch, with the phone in your pocket). */
    fun inFront(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun installed(context: Context, pkg: String) = context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Which app the person asked for, in the words they used. */
    fun appOf(said: String): String? {
        val t = Spoken.plain(said)
        return when {
            "spotify" in t -> SPOTIFY
            "youtube" in t && "music" in t -> YT_MUSIC
            "youtube" in t || "you tube" in t -> YOUTUBE
            else -> null
        }
    }

    /**
     * Plays [what]: Spotify and YouTube Music start playing it, YouTube shows
     * its results to pick from. Returns the app's name, or null if none could.
     */
    fun play(context: Context, what: String, app: String?): String? {
        val order = app?.let { listOf(it) } ?: listOf(SPOTIFY, YT_MUSIC, YOUTUBE)
        for (pkg in order) {
            if (!installed(context, pkg)) continue
            val ok = when (pkg) {
                YOUTUBE -> start(context, Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", what))
                else -> start(
                    context,
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg)
                        .putExtra(SearchManager.QUERY, what)
                        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*"),
                ) || (pkg == SPOTIFY && start(context, Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(what)))))
            }
            if (ok) return name(pkg)
        }
        // None of them installed: YouTube on the web.
        return if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(what))))) "YouTube" else null
    }

    fun name(pkg: String) = when (pkg) {
        SPOTIFY -> "Spotify"
        YT_MUSIC -> "YouTube Music"
        else -> "YouTube"
    }

    /** A search in the browser. */
    fun search(context: Context, query: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
}

/**
 * Looking something up on Wikipedia, so Tokkip can tell you about it in the
 * chat. Off until you turn on "Permitir buscar en internet": then, and only
 * when you ask, she reads Spanish Wikipedia's summary of the topic.
 */
object Web {
    private const val PREFS = "asistente_web"

    fun allowed(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("permitir", false)

    fun setAllowed(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("permitir", on).apply()

    /** (title, summary, link) of the article that best fits [topic], or null. */
    fun wikipedia(topic: String): Triple<String, String, String>? {
        val found = JSONObject(get("https://es.wikipedia.org/w/rest.php/v1/search/page?limit=1&q=" + enc(topic)))
            .optJSONArray("pages")?.optJSONObject(0) ?: return null
        val key = found.optString("key").ifEmpty { return null }
        val page = JSONObject(get("https://es.wikipedia.org/api/rest_v1/page/summary/" + enc(key)))
        val text = page.optString("extract").trim().ifEmpty { return null }
        val link = page.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page").orEmpty()
        return Triple(page.optString("title", found.optString("title")), text, link)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "Tokkip/2 (asistente personal para Android)")
        c.setRequestProperty("Accept", "application/json")
        return try {
            if (c.responseCode !in 200..299) error("Wikipedia respondió ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
