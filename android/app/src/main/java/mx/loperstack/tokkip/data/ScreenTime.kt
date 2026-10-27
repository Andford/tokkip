// Bienestar → Pantalla: how long you're on the phone, in which apps and what
// kind (social, video, games…), and Tokkip's doomscrolling indicator: four
// faces from calm to dizzy, from your minutes in "scroll" apps against your
// daily goal, how long you've been at it without a break, and the hour (late
// at night counts more). The numbers come from Android's usage access
// (data/ScreenUsage.kt); this part is the arithmetic.
// Pure Kotlin: tested in app/src/test.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import mx.loperstack.tokkip.wellness.DOOM_LEVELS
import mx.loperstack.tokkip.wellness.DoomLevel

enum class AppKind(val label: String, val icon: String) {
    SOCIAL("Redes", "social"), VIDEO("Video", "video"), GAMES("Juegos", "games"), NEWS("Noticias", "news"),
    MESSAGES("Mensajes", "chat"), MUSIC("Música", "music"), PRODUCTIVITY("Productividad", "work"),
    MAPS("Mapas", "map"), OTHER("Otras", "apps"),
}

/** Known apps, by package: Android's own category doesn't always say. */
private val KNOWN = mapOf(
    "com.instagram.android" to AppKind.SOCIAL, "com.instagram.barcelona" to AppKind.SOCIAL, "com.facebook.katana" to AppKind.SOCIAL,
    "com.facebook.lite" to AppKind.SOCIAL, "com.zhiliaoapp.musically" to AppKind.SOCIAL, "com.ss.android.ugc.trill" to AppKind.SOCIAL,
    "com.twitter.android" to AppKind.SOCIAL, "com.reddit.frontpage" to AppKind.SOCIAL, "com.snapchat.android" to AppKind.SOCIAL,
    "com.pinterest" to AppKind.SOCIAL, "com.linkedin.android" to AppKind.SOCIAL, "com.bereal.ft" to AppKind.SOCIAL,
    "com.tumblr" to AppKind.SOCIAL, "com.discord" to AppKind.MESSAGES,
    "com.google.android.youtube" to AppKind.VIDEO, "com.netflix.mediaclient" to AppKind.VIDEO, "com.disney.disneyplus" to AppKind.VIDEO,
    "com.amazon.avod.thirdpartyclient" to AppKind.VIDEO, "tv.twitch.android.app" to AppKind.VIDEO, "com.hbo.hbonow" to AppKind.VIDEO,
    "com.wbd.stream" to AppKind.VIDEO, "com.vix.app" to AppKind.VIDEO, "com.google.android.apps.youtube.kids" to AppKind.VIDEO,
    "com.whatsapp" to AppKind.MESSAGES, "com.whatsapp.w4b" to AppKind.MESSAGES, "org.telegram.messenger" to AppKind.MESSAGES,
    "com.facebook.orca" to AppKind.MESSAGES, "com.google.android.apps.messaging" to AppKind.MESSAGES, "org.thoughtcrime.securesms" to AppKind.MESSAGES,
    "com.spotify.music" to AppKind.MUSIC, "com.google.android.apps.youtube.music" to AppKind.MUSIC,
    "com.google.android.gm" to AppKind.PRODUCTIVITY, "com.google.android.calendar" to AppKind.PRODUCTIVITY,
    "com.google.android.apps.docs" to AppKind.PRODUCTIVITY, "com.microsoft.teams" to AppKind.PRODUCTIVITY, "com.slack" to AppKind.PRODUCTIVITY,
    "com.google.android.apps.maps" to AppKind.MAPS, "com.waze" to AppKind.MAPS,
)

/** An app's kind: known ones first, then Android's category (ApplicationInfo.CATEGORY_*). */
fun kindOf(pkg: String, category: Int): AppKind = KNOWN[pkg] ?: when (category) {
    0 -> AppKind.GAMES
    1 -> AppKind.MUSIC
    2 -> AppKind.VIDEO
    4 -> AppKind.SOCIAL
    5 -> AppKind.NEWS
    6 -> AppKind.MAPS
    7 -> AppKind.PRODUCTIVITY
    else -> AppKind.OTHER
}

/** One moment from Android's usage log: an app came to the front (true) or left it (false); `pkg` null = the screen went off. */
data class UseEvent(val at: Long, val pkg: String?, val front: Boolean)

/** A stretch of time in one app. */
data class UseSpan(val pkg: String, val start: Long, val end: Long) {
    val ms get() = end - start
}

/** The log as spans: each app from when it came to the front until another did, it left, or the screen went off. Open spans end at `now`. */
fun spansOf(events: List<UseEvent>, now: Long): List<UseSpan> {
    val out = ArrayList<UseSpan>()
    var cur: String? = null
    var since = 0L
    fun close(at: Long) { cur?.let { if (at > since) out += UseSpan(it, since, at) }; cur = null }
    for (e in events.sortedBy { it.at }) {
        when {
            e.pkg == null -> close(e.at)
            e.front -> { if (cur != e.pkg) { close(e.at); cur = e.pkg; since = e.at } }
            e.pkg == cur -> close(e.at)
        }
    }
    close(now)
    return out
}

/** Spans of the same app with short gaps (switching activities, a quick notification) become one session. */
fun sessionsOf(spans: List<UseSpan>, gapMs: Long = 60_000): List<UseSpan> {
    val out = ArrayList<UseSpan>()
    for (s in spans.sortedBy { it.start }) {
        val last = out.lastOrNull()
        if (last != null && last.pkg == s.pkg && s.start - last.end <= gapMs) out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end))
        else out += s
    }
    return out
}

data class AppUse(val pkg: String, val label: String, val kind: AppKind, val minutes: Int)

/** Your settings for Pantalla. */
data class ScreenSettings(
    /** Minutes a day in scroll apps you're fine with. */
    val goal: Int = 60,
    /** A nudge after this many minutes in a row in a scroll app; 0 = off. */
    val nudgeAfter: Int = 20,
    /** Which kinds count as scrolling. */
    val scrollKinds: Set<AppKind> = setOf(AppKind.SOCIAL, AppKind.VIDEO),
)

data class ScreenDay(
    val day: String,
    val totalMin: Int,
    val scrollMin: Int,
    /** Scrolling between 23:00 and 5:00. */
    val lateMin: Int = 0,
    val unlocks: Int = 0,
)

data class ScreenToday(
    val totalMin: Int,
    val scrollMin: Int,
    val unlocks: Int,
    val apps: List<AppUse>,
    val byKind: List<Pair<AppKind, Int>>,
    /** Minutes per hour of the day (0…23). */
    val hourly: List<Int>,
    /** The scroll session going on now (if the phone is in a scroll app), and its app. */
    val sessionMin: Int,
    val sessionApp: String?,
    val longestMin: Int,
    val lateMin: Int,
)

/** The level: your scroll minutes against the goal, the session going on, and the hour. */
fun doomLevel(scrollMin: Int, goal: Int, sessionMin: Int, hour: Int): Int {
    val g = goal.coerceAtLeast(10)
    var lvl = when {
        scrollMin < g * 0.5 -> 0
        scrollMin < g -> 1
        scrollMin < g * 1.5 -> 2
        else -> 3
    }
    lvl = maxOf(lvl, when { sessionMin >= 40 -> 3; sessionMin >= 20 -> 2; sessionMin >= 10 -> 1; else -> 0 })
    if ((hour >= 23 || hour < 5) && sessionMin >= 10) lvl += 1
    return lvl.coerceIn(0, 3)
}

private fun minutes(ms: Long) = (ms / 60_000).toInt()

/**
 * Today from the spans: totals, apps, kinds, hours, the session going on and
 * the longest one. `label` and `kind` look apps up; `ignore` drops launchers.
 */
fun summarize(
    spans: List<UseSpan>, dayStart: Long, now: Long, s: ScreenSettings, unlocks: Int,
    label: (String) -> String, kind: (String) -> AppKind, ignore: (String) -> Boolean,
): ScreenToday {
    val today = spans.filter { !ignore(it.pkg) }.mapNotNull { sp ->
        val a = maxOf(sp.start, dayStart); val b = minOf(sp.end, now)
        if (b > a) sp.copy(start = a, end = b) else null
    }
    val perApp = today.groupBy { it.pkg }.mapValues { (_, v) -> v.sumOf { it.ms } }
    val apps = perApp.entries.sortedByDescending { it.value }.map { (p, ms) -> AppUse(p, label(p), kind(p), minutes(ms)) }.filter { it.minutes > 0 }
    val byKind = perApp.entries.groupBy { kind(it.key) }.mapValues { (_, v) -> minutes(v.sumOf { it.value }) }
        .filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key to it.value }
    val hourly = IntArray(24)
    val c = Calendar.getInstance()
    for (sp in today) {
        var t = sp.start
        while (t < sp.end) {
            c.timeInMillis = t
            val h = c.get(Calendar.HOUR_OF_DAY)
            c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.add(Calendar.HOUR_OF_DAY, 1)
            val stop = minOf(sp.end, c.timeInMillis)
            hourly[h] += minutes(stop - t).coerceAtLeast(if (stop - t >= 30_000) 1 else 0)
            t = stop
        }
    }
    val scroll = today.filter { kind(it.pkg) in s.scrollKinds }
    val sessions = sessionsOf(scroll)
    val open = sessions.lastOrNull()?.takeIf { it.end >= now - 1_000 }
    val late = scroll.sumOf { sp ->
        var ms = 0L; var t = sp.start
        while (t < sp.end) {
            c.timeInMillis = t
            val h = c.get(Calendar.HOUR_OF_DAY)
            val step = minOf(sp.end, t + 60_000)
            if (h >= 23 || h < 5) ms += step - t
            t = step
        }
        ms
    }
    return ScreenToday(
        totalMin = minutes(today.sumOf { it.ms }),
        scrollMin = minutes(scroll.sumOf { it.ms }),
        unlocks = unlocks,
        apps = apps,
        byKind = byKind,
        hourly = hourly.map { it.coerceAtMost(60) },
        sessionMin = open?.let { minutes(it.ms) } ?: 0,
        sessionApp = open?.pkg,
        longestMin = sessions.maxOfOrNull { minutes(it.ms) } ?: 0,
        lateMin = minutes(late),
    )
}

/** Tokkip's notes on your screen time: today's level, late nights, and how it lines up with sleep and mood. */
fun screenNotes(days: List<ScreenDay>, today: ScreenDay?, goal: Int, nights: List<Night>, moods: Map<String, Int>, now: Long): List<TokkipNote> {
    val out = ArrayList<TokkipNote>()
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val hour = c.get(Calendar.HOUR_OF_DAY)
    if (today != null) {
        val lvl = doomLevel(today.scrollMin, goal, 0, hour)
        if (lvl >= 3) out += TokkipNote("screen-dizzy", "phone", "Hoy llevas ${hoursText(today.scrollMin)} de scroll; tu meta es ${hoursText(goal)}.",
            "Deja el teléfono en otro cuarto un rato. Tokkip ya anda mareado.", 60)
        else if (lvl == 2) out += TokkipNote("screen-tired", "phone", "Ya pasaste tu meta de scroll de hoy (${hoursText(today.scrollMin)}).",
            "Cambia el próximo rato de redes por una caminata o una respiración.", 50)
    }
    val past = days.filter { it.day < dayKey(now) }.sortedBy { it.day }.takeLast(14)
    val lateNights = past.takeLast(7).count { it.lateMin >= 20 }
    if (lateNights >= 3) out += TokkipNote("screen-late", "night", "En $lateNights de las últimas 7 noches hiciste scroll después de las 11.",
        "Deja el teléfono cargando lejos de la cama: tu sueño te lo agradece.", 52)
    // Scroll at night and the next morning's sleep.
    val byDay = past.associateBy { it.day }
    val pairs = nights.mapNotNull { n ->
        val prev = Calendar.getInstance().apply { timeInMillis = dayStart(n.day); add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        byDay[dayKey(prev)]?.let { it.lateMin to n.minutes }
    }
    val late = pairs.filter { it.first >= 20 }.map { it.second }
    val early = pairs.filter { it.first < 5 }.map { it.second }
    if (late.size >= 3 && early.size >= 3 && early.average() - late.average() >= 25) out += TokkipNote("screen-sleep", "night",
        "Las noches con scroll tarde duermes ${(early.average() - late.average()).toInt()} min menos.",
        "Pon una hora de \"teléfono a dormir\" 30 min antes que tú.", 55)
    // Scroll and mood.
    val withMood = past.filter { moods[it.day] != null }
    val heavy = withMood.filter { it.scrollMin > goal }.mapNotNull { moods[it.day] }
    val light = withMood.filter { it.scrollMin <= goal }.mapNotNull { moods[it.day] }
    if (heavy.size >= 3 && light.size >= 3 && light.average() - heavy.average() >= 0.5) out += TokkipNote("screen-mood", "heart",
        "Los días con menos scroll te sientes mejor (${"%.1f".format(java.util.Locale.US, light.average())} contra ${"%.1f".format(java.util.Locale.US, heavy.average())}).",
        "Menos pantalla, mejor ánimo: es tu propio dato.", 54)
    val under = past.takeLast(7).count { it.scrollMin <= goal }
    if (past.size >= 5 && under >= 5) out += TokkipNote("screen-good", "sparkle", "$under de los últimos 7 días te quedaste dentro de tu meta de scroll.",
        "¡Eso es control! Tokkip está tranquilo.", 32)
    return out
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun screenSettingsJson(s: ScreenSettings): String = JSONObject()
    .put("goal", s.goal).put("nudge", s.nudgeAfter).put("kinds", JSONArray(s.scrollKinds.map { it.name })).toString()

fun screenSettingsOf(raw: String?): ScreenSettings = runCatching {
    val o = JSONObject(raw ?: return ScreenSettings())
    val k = o.optJSONArray("kinds")
    ScreenSettings(
        o.optInt("goal", 60).coerceIn(10, 600), o.optInt("nudge", 20).coerceIn(0, 120),
        (0 until (k?.length() ?: 0)).mapNotNull { runCatching { AppKind.valueOf(k!!.optString(it)) }.getOrNull() }.toSet().ifEmpty { setOf(AppKind.SOCIAL, AppKind.VIDEO) },
    )
}.getOrDefault(ScreenSettings())

fun screenDaysJson(list: List<ScreenDay>): String = JSONArray().apply {
    for (d in list) put(JSONObject().put("d", d.day).put("t", d.totalMin).put("s", d.scrollMin).put("l", d.lateMin).put("u", d.unlocks))
}.toString()

fun screenDaysOf(raw: String?): List<ScreenDay> = runCatching {
    val a = JSONArray(raw ?: return emptyList())
    (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val d = o.optString("d")
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(d)) null
        else ScreenDay(d, o.optInt("t").coerceIn(0, 1440), o.optInt("s").coerceIn(0, 1440), o.optInt("l").coerceIn(0, 1440), o.optInt("u").coerceAtLeast(0))
    }.sortedBy { it.day }.takeLast(120)
}.getOrDefault(emptyList())
