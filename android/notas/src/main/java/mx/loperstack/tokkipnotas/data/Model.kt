// Tokkip Notas' model, plain Kotlin (tested in src/test).
//
// Everything is a page. A page can hold other pages (as many levels as you
// like), and is one of two kinds:
//   DOC        — text in lines (blocks): headings, bullets, steps to tick…
//   COLLECTION — a set of cards (its child pages) with columns: the same cards
//                seen as a board (grouped by their status) or as a table.
// A card is an ordinary page with values for its collection's columns, so it
// opens as a page with its own notes.
//
// A page's body is plain text, one block per line (see Blocks.kt), so search,
// copies and a future Windows app stay simple. Only the index (titles,
// columns, values, a preview) lives in memory; bodies are read on open.

package mx.loperstack.tokkipnotas.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** DOC: lines of text. COLLECTION: cards as a board or table. INK: a page written by hand (its body is a drawing, Ink.kt). */
enum class PageKind { DOC, COLLECTION, INK }

enum class PropType(val label: String) { TEXT("Texto"), NUMBER("Número"), SELECT("Estado"), MULTI("Etiquetas"), DATE("Fecha"), CHECK("Casilla") }

data class Opt(val id: String, val name: String, val color: Long)

data class Prop(val id: String, val name: String, val type: PropType, val options: List<Opt> = emptyList())

/** A collection's columns and how it's shown. `groupBy` is the SELECT column the board groups by. */
data class Schema(
    val props: List<Prop> = emptyList(),
    val view: String = "board",
    val groupBy: String = "",
    /** The table's sort: a column id (or "title"), and whether it goes down. */
    val sortBy: String = "",
    val sortDesc: Boolean = false,
) {
    fun prop(id: String) = props.firstOrNull { it.id == id }
    val group: Prop? get() = prop(groupBy)?.takeIf { it.type == PropType.SELECT } ?: props.firstOrNull { it.type == PropType.SELECT }
}

/** A page in the index: everything except its body. */
data class Page(
    val id: String,
    val parent: String?,
    val title: String = "",
    /** Icon key (ui/Icons.kt), with the colour and Expressive shape it sits in. */
    val icon: String = "doc",
    val color: Long = 0xFFA78BFA,
    val shape: Int = 0,
    val kind: PageKind = PageKind.DOC,
    /** Position among its siblings (and in a board column). */
    val ord: Double = 0.0,
    val created: Long = 0,
    val updated: Long = 0,
    val opened: Long = 0,
    val favorite: Boolean = false,
    /** When it went to the Papelera (0 = it didn't). */
    val trashed: Long = 0,
    /** A card's values, by column id. */
    val props: Map<String, String> = emptyMap(),
    /** A collection's columns. */
    val schema: Schema? = null,
    val preview: String = "",
    val steps: Int = 0,
    val done: Int = 0,
) {
    val shownTitle get() = title.ifBlank { preview.take(40).ifBlank { "Sin título" } }
}

const val INBOX_ID = "inbox"
const val DAY_MS = 86_400_000L

// ── Colours for options and pages ─────────────────────────────────────────────

val PALETTE = listOf(0xFFA78BFA, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFF97316, 0xFFF472B6, 0xFF22D3EE, 0xFFF87171, 0xFF94A3B8)
const val GREY = 0xFF94A3B8

/** The columns a new collection starts with: Estado (Por hacer · Haciendo · Hecho), Fecha and Etiquetas. */
fun defaultSchema(view: String = "board"): Schema = Schema(
    props = listOf(
        Prop("status", "Estado", PropType.SELECT, listOf(Opt("todo", "Por hacer", GREY), Opt("doing", "Haciendo", 0xFFFBBF24), Opt("done", "Hecho", 0xFF34D399))),
        Prop("date", "Fecha", PropType.DATE),
        Prop("tags", "Etiquetas", PropType.MULTI),
    ),
    view = view, groupBy = "status",
)

/** Is this option the "finished" one? The last option of the status column, by convention (Hecho). */
fun Schema.isDone(optId: String?): Boolean {
    val g = group ?: return false
    return optId != null && g.options.lastOrNull()?.id == optId
}

// ── The tree ──────────────────────────────────────────────────────────────────

data class Index(val pages: List<Page> = emptyList()) {
    private val byId by lazy { pages.associateBy { it.id } }
    private val kids by lazy { pages.filter { it.trashed == 0L }.groupBy { it.parent } }
    fun page(id: String?) = id?.let { byId[it] }
    /** A page's living children, in order. */
    fun children(id: String?): List<Page> = (kids[id] ?: emptyList()).sortedWith(compareBy<Page> { it.ord }.thenBy { it.created })
    val roots get() = children(null).filter { it.id != INBOX_ID }
    val inbox get() = children(INBOX_ID)
    val live get() = pages.filter { it.trashed == 0L && it.id != INBOX_ID }
    val trash get() = pages.filter { it.trashed > 0 && (page(it.parent)?.trashed ?: 0L) != it.trashed }.sortedByDescending { it.trashed }

    /** From the top down to the page's parent. */
    fun path(id: String): List<Page> {
        val out = ArrayList<Page>()
        var p = page(page(id)?.parent)
        var guard = 0
        while (p != null && guard++ < 64) { out.add(0, p); p = page(p.parent) }
        return out
    }

    /** The page and everything under it (trashed or not). */
    fun subtree(id: String): List<Page> {
        val all = pages.groupBy { it.parent }
        val out = ArrayList<Page>()
        fun walk(i: String) { page(i)?.let { out += it }; all[i]?.forEach { walk(it.id) } }
        walk(id)
        return out
    }

    /** Is `a` the same as, or inside, `b`? (A page can't move into itself.) */
    fun isInside(a: String, b: String): Boolean = a == b || path(a).any { it.id == b }

    /** The collection a card belongs to, if it is one. */
    fun collectionOf(p: Page): Page? = page(p.parent)?.takeIf { it.kind == PageKind.COLLECTION }

    val recent get() = live.filter { it.opened > 0 }.sortedByDescending { it.opened }
    val favorites get() = live.filter { it.favorite }.sortedBy { it.title.lowercase() }
}

/** An order value that places a page after `before` and before `after` (either may be missing). */
fun between(before: Double?, after: Double?): Double = when {
    before == null && after == null -> 0.0
    before == null -> after!! - 1.0
    after == null -> before + 1.0
    else -> (before + after) / 2.0
}

// ── Board ─────────────────────────────────────────────────────────────────────

/** One board column: an option of the status column (or "" for the cards without one). */
data class Lane(val opt: Opt?, val cards: List<Page>)

fun Index.board(c: Page): List<Lane> {
    val s = c.schema ?: return emptyList()
    val g = s.group ?: return listOf(Lane(null, children(c.id)))
    val cards = children(c.id)
    val known = g.options.map { it.id }.toSet()
    val lanes = g.options.map { o -> Lane(o, cards.filter { it.props[g.id] == o.id }) }
    val loose = cards.filter { it.props[g.id] !in known }
    return if (loose.isEmpty()) lanes else listOf(Lane(null, loose)) + lanes
}

/** What a card becomes when dropped in `lane` before the card at `at` (or at the end). */
fun moveCard(card: Page, group: Prop, lane: Lane, at: Int): Page {
    val others = lane.cards.filter { it.id != card.id }
    val i = at.coerceIn(0, others.size)
    val ord = between(others.getOrNull(i - 1)?.ord, others.getOrNull(i)?.ord)
    val props = if (lane.opt == null) card.props - group.id else card.props + (group.id to lane.opt.id)
    return card.copy(ord = ord, props = props)
}

// ── Table ─────────────────────────────────────────────────────────────────────

fun Index.rows(c: Page): List<Page> {
    val s = c.schema ?: return children(c.id)
    val rows = children(c.id)
    if (s.sortBy.isEmpty()) return rows
    val prop = s.prop(s.sortBy)
    val cmp: Comparator<Page> = when {
        s.sortBy == "title" -> compareBy { fold(it.shownTitle) }
        prop == null -> return rows
        prop.type == PropType.NUMBER -> compareBy(nullsLast()) { it.props[prop.id]?.toDoubleOrNull() }
        prop.type == PropType.SELECT -> compareBy { r -> prop.options.indexOfFirst { it.id == r.props[prop.id] }.let { if (it < 0) Int.MAX_VALUE else it } }
        prop.type == PropType.CHECK -> compareBy { it.props[prop.id] != "1" }
        else -> compareBy(nullsLast()) { it.props[prop.id]?.ifBlank { null }?.let(::fold) }
    }
    return rows.sortedWith(if (s.sortDesc) cmp.reversed() else cmp)
}

/** How a value reads in a cell. */
fun display(prop: Prop, raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return when (prop.type) {
        PropType.SELECT -> prop.options.firstOrNull { it.id == raw }?.name ?: ""
        PropType.MULTI -> multiOf(raw).mapNotNull { id -> prop.options.firstOrNull { it.id == id }?.name }.joinToString(", ")
        PropType.DATE -> shortDate(raw)
        PropType.CHECK -> if (raw == "1") "✓" else ""
        else -> raw
    }
}

fun multiOf(raw: String?): List<String> = raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

// ── Dates (day keys, yyyy-MM-dd) ──────────────────────────────────────────────

fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
fun dayStart(day: String): Long = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!.time }.getOrDefault(0L)

private val ES = Locale("es", "MX")

/** "hoy", "mañana", "ayer", "vie 31 oct". */
fun shortDate(day: String, now: Long = System.currentTimeMillis()): String {
    val t = dayStart(day)
    if (t == 0L) return day
    val today = dayStart(dayKey(now))
    return when (Math.round((t - today) / DAY_MS.toDouble()).toInt()) {
        0 -> "hoy"
        1 -> "mañana"
        -1 -> "ayer"
        else -> SimpleDateFormat("EEE d MMM", ES).format(Date(t)).replace(".", "")
    }
}

data class Due(val card: Page, val collection: Page, val day: String)

/** Cards with a date up to `days` ahead (and the late ones) that aren't done: "Para hoy". */
fun Index.dueSoon(now: Long, days: Int = 1): List<Due> {
    val limit = dayKey(now + days * DAY_MS)
    val out = ArrayList<Due>()
    for (c in live.filter { it.kind == PageKind.COLLECTION }) {
        val s = c.schema ?: continue
        val dateProp = s.props.firstOrNull { it.type == PropType.DATE } ?: continue
        val g = s.group
        for (r in children(c.id)) {
            val d = r.props[dateProp.id]?.takeIf { it.isNotBlank() } ?: continue
            if (d > limit) continue
            if (g != null && s.isDone(r.props[g.id])) continue
            out += Due(r, c, d)
        }
    }
    return out.sortedBy { it.day }
}

// ── Search ────────────────────────────────────────────────────────────────────

fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

/** Every word of the query must appear (accents and case don't matter); titles weigh more. */
fun searchScore(query: String, title: String, body: String): Int {
    val words = fold(query).split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return 0
    val t = fold(title)
    val b = fold(body)
    var score = 0
    for (w in words) {
        score += when {
            t.startsWith(w) -> 6
            t.contains(w) -> 4
            b.contains(w) -> 1
            else -> return 0
        }
    }
    return score
}

fun snippet(query: String, body: String, width: Int = 80): String {
    val flat = body.replace('\n', ' ')
    val w = fold(query).split(Regex("""\s+""")).firstOrNull { it.isNotEmpty() } ?: return flat.take(width)
    val i = fold(flat).indexOf(w)
    if (i < 0) return flat.take(width)
    val from = (i - width / 3).coerceAtLeast(0)
    val to = (from + width).coerceAtMost(flat.length)
    return (if (from > 0) "…" else "") + flat.substring(from, to).trim() + (if (to < flat.length) "…" else "")
}

// ── JSON (database columns and copies) ────────────────────────────────────────

fun schemaJson(s: Schema?): String = if (s == null) "" else JSONObject()
    .put("view", s.view).put("groupBy", s.groupBy).put("sortBy", s.sortBy).put("sortDesc", s.sortDesc)
    .put("props", JSONArray().apply {
        for (p in s.props) put(JSONObject().put("id", p.id).put("name", p.name).put("type", p.type.name).put("options", JSONArray().apply {
            for (o in p.options) put(JSONObject().put("id", o.id).put("name", o.name).put("color", o.color))
        }))
    }).toString()

fun schemaOf(raw: String?): Schema? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val o = JSONObject(raw)
        val props = o.optJSONArray("props") ?: JSONArray()
        Schema(
            props = (0 until props.length()).mapNotNull { i ->
                val p = props.optJSONObject(i) ?: return@mapNotNull null
                val type = runCatching { PropType.valueOf(p.optString("type")) }.getOrDefault(PropType.TEXT)
                val opts = p.optJSONArray("options") ?: JSONArray()
                Prop(p.optString("id"), p.optString("name"), type, (0 until opts.length()).mapNotNull { j ->
                    opts.optJSONObject(j)?.let { Opt(it.optString("id"), it.optString("name"), it.optLong("color", GREY)) }
                })
            },
            view = o.optString("view", "board"), groupBy = o.optString("groupBy"), sortBy = o.optString("sortBy"), sortDesc = o.optBoolean("sortDesc"),
        )
    }.getOrNull()
}

fun propsJson(m: Map<String, String>): String = if (m.isEmpty()) "" else JSONObject().apply { for ((k, v) in m) put(k, v) }.toString()

fun propsOf(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching { val o = JSONObject(raw); o.keys().asSequence().associateWith { o.optString(it) } }.getOrDefault(emptyMap())
}

/** A short random id. */
fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)

/** Today, in Calendar terms, for the greeting. */
fun hourOf(now: Long) = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
