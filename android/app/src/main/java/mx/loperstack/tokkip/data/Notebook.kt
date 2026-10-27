// Cuaderno: notes and projects, made for a busy head. You jot something down
// in one tap (it waits in the Bandeja); projects are little boxes with a
// colour, a shape and an icon, and their notes' checklists are the steps.
// What Tokkip shows first is the next step and what you left half-done, so
// picking things up again costs nothing.
//
// A note's body is plain text, one line per block:
//   "# Título"   a heading
//   "• algo"     a bullet
//   "[ ] paso"   a step to do,  "[x] paso" a step done
//   anything else is a paragraph.
// Plain text keeps search, the copy (.zip) and a future Windows app simple.
//
// Only the index (titles, a preview, the step counts) is kept in memory; a
// note's body is read from the phone's database when you open it
// (data/NotebookDb.kt). Pure Kotlin: tested in app/src/test.

package mx.loperstack.tokkip.data

import java.text.Normalizer
import java.util.Calendar

enum class BlockKind(val prefix: String) { TEXT(""), HEADING("# "), BULLET("• "), TODO("[ ] "), DONE("[x] ") }

data class Block(val kind: BlockKind, val text: String) {
    val isStep get() = kind == BlockKind.TODO || kind == BlockKind.DONE
    fun line() = kind.prefix + text
}

fun blocksOf(body: String): List<Block> {
    if (body.isEmpty()) return listOf(Block(BlockKind.TEXT, ""))
    return body.split('\n').map { l ->
        when {
            l.startsWith("[x] ") || l.startsWith("[X] ") -> Block(BlockKind.DONE, l.substring(4))
            l.startsWith("[ ] ") -> Block(BlockKind.TODO, l.substring(4))
            l.startsWith("# ") -> Block(BlockKind.HEADING, l.substring(2))
            l.startsWith("• ") || l.startsWith("- ") || l.startsWith("* ") -> Block(BlockKind.BULLET, l.substring(2))
            else -> Block(BlockKind.TEXT, l)
        }
    }
}

fun bodyOf(blocks: List<Block>): String = blocks.joinToString("\n") { it.line() }.trimEnd('\n')

/** A note's line in the index: what lists, search and Tokkip need, without the body. */
data class NoteHead(
    val id: String,
    /** The project it lives in; null = in the Bandeja. */
    val project: String?,
    val title: String,
    val preview: String,
    val steps: Int,
    val done: Int,
    val pinned: Boolean,
    val created: Long,
    val updated: Long,
    val opened: Long,
    /** The first step still to do, for "lo siguiente". */
    val next: String = "",
) {
    val inInbox get() = project == null
    val halfDone get() = steps > 0 && done in 1 until steps
    val shownTitle get() = title.ifBlank { preview.lineSequence().firstOrNull()?.take(40)?.ifBlank { null } ?: "Sin título" }
}

data class Project(
    val id: String,
    val name: String,
    val color: Long,
    /** Index into MShapes.ALL. */
    val shape: Int,
    /** An icon key (ui/Notebook.kt PROJECT_ICONS). */
    val icon: String,
    /** Why it matters, in your words: shown at the top, so you remember. */
    val why: String = "",
    /** A day key, or "". */
    val due: String = "",
    val created: Long,
    val updated: Long,
    /** When you called it done (0 = still going). */
    val doneAt: Long = 0,
    val archived: Boolean = false,
    val order: Int = 0,
)

/** What one day of Cuaderno did, for Tokkip's growth. */
data class NotebookDay(val day: String, val edits: Int, val steps: Int, val finished: Int)

/** Everything the Cuaderno keeps in memory. */
data class NotebookIndex(
    val projects: List<Project> = emptyList(),
    val notes: List<NoteHead> = emptyList(),
    val days: List<NotebookDay> = emptyList(),
) {
    fun notesOf(project: String?) = notes.filter { it.project == project }
    val inbox get() = notesOf(null)
    val active get() = projects.filter { !it.archived && it.doneAt == 0L }.sortedWith(compareBy<Project> { it.order }.thenByDescending { it.updated })
    val finished get() = projects.filter { it.doneAt > 0 && !it.archived }.sortedByDescending { it.doneAt }
}

data class ProjectProgress(val steps: Int, val done: Int, val notes: Int, val next: NoteHead?, val touched: Long) {
    val frac get() = if (steps == 0) 0f else done.toFloat() / steps
    val complete get() = steps > 0 && done == steps
}

fun NotebookIndex.progress(p: Project): ProjectProgress {
    val ns = notesOf(p.id)
    val next = ns.filter { it.next.isNotBlank() }.maxByOrNull { maxOf(it.opened, it.updated) }
    return ProjectProgress(ns.sumOf { it.steps }, ns.sumOf { it.done }, ns.size, next, maxOf(p.updated, ns.maxOfOrNull { it.updated } ?: 0))
}

/** "Lo que dejaste a medias": notes with steps left, the freshest first; then the last ones you opened. */
fun NotebookIndex.halfDone(now: Long, n: Int = 3): List<NoteHead> {
    val live = projects.filter { it.doneAt == 0L && !it.archived }.map { it.id }.toSet()
    val ok = notes.filter { it.project == null || it.project in live }
    val weekAgo = now - 14 * DAY_MS
    val withSteps = ok.filter { it.next.isNotBlank() && maxOf(it.opened, it.updated) >= weekAgo }
        .sortedByDescending { maxOf(it.opened, it.updated) }
    val rest = ok.filter { it !in withSteps && it.opened >= now - 3 * DAY_MS }.sortedByDescending { it.opened }
    return (withSteps + rest).take(n)
}

private const val DAY_MS = 86_400_000L

/** The first line of a quick capture becomes the title; the rest, the body. */
fun captureOf(text: String): Pair<String, String> {
    val lines = text.trim().lines()
    val first = lines.firstOrNull().orEmpty().trim()
    return if (first.length <= 60) first to lines.drop(1).joinToString("\n").trim()
    else "" to text.trim()
}

/** The title, a short preview and the steps of a body. */
data class NoteFacts(val preview: String, val steps: Int, val done: Int, val next: String)

fun factsOf(body: String): NoteFacts {
    val blocks = blocksOf(body)
    val steps = blocks.count { it.isStep }
    val done = blocks.count { it.kind == BlockKind.DONE }
    val next = blocks.firstOrNull { it.kind == BlockKind.TODO && it.text.isNotBlank() }?.text.orEmpty()
    val preview = blocks.filter { it.text.isNotBlank() }.take(3).joinToString(" · ") { it.text.trim() }.take(140)
    return NoteFacts(preview, steps, done, next)
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

/** A short piece of `body` around the first word of the query found. */
fun snippet(query: String, body: String, width: Int = 80): String {
    val flat = body.replace('\n', ' ')
    val w = fold(query).split(Regex("""\s+""")).firstOrNull { it.isNotEmpty() } ?: return flat.take(width)
    val i = fold(flat).indexOf(w)
    if (i < 0) return flat.take(width)
    val from = (i - width / 3).coerceAtLeast(0)
    val to = (from + width).coerceAtMost(flat.length)
    return (if (from > 0) "…" else "") + flat.substring(from, to).trim() + (if (to < flat.length) "…" else "")
}

// ── Tokkip ────────────────────────────────────────────────────────────────────

/** What Tokkip notices in the Cuaderno (joins "Tokkip notó"). */
fun notebookNotes(nb: NotebookIndex, now: Long): List<TokkipNote> {
    val out = ArrayList<TokkipNote>()
    val inbox = nb.inbox.size
    if (inbox >= 6) out += TokkipNote(
        "nb-inbox", "write", "Tienes $inbox ideas esperando en la Bandeja.",
        "Cinco minutos para acomodarlas en sus proyectos y tu cabeza queda más ligera.", 34,
    )
    // A project with steps left that you haven't touched in a week.
    val stale = nb.active.map { it to nb.progress(it) }
        .filter { (_, pr) -> pr.steps > pr.done && now - pr.touched > 7 * DAY_MS }
        .maxByOrNull { (_, pr) -> pr.touched }
    if (stale != null) {
        val (p, pr) = stale
        val days = ((now - pr.touched) / DAY_MS).toInt()
        val step = pr.next?.next?.let { "«$it»" } ?: "el siguiente paso"
        out += TokkipNote(
            "nb-stale", "star", "«${p.name}» lleva $days días quieto.",
            "No tienes que terminarlo hoy: solo $step. Diez minutos y ya avanzaste.", 32,
        )
    }
    val week = now - 7 * DAY_MS
    val doneWeek = nb.projects.count { it.doneAt >= week }
    if (doneWeek > 0) out += TokkipNote(
        "nb-finished", "party",
        if (doneWeek == 1) "Esta semana terminaste un proyecto." else "Esta semana terminaste $doneWeek proyectos.",
        "Terminar cosas es lo difícil. Date un ratito para disfrutarlo antes de empezar otra.", 36,
    )
    val stepsWeek = nb.days.filter { it.day >= dayKey(week) }.sumOf { it.steps }
    if (stepsWeek >= 10 && doneWeek == 0) out += TokkipNote(
        "nb-steps", "flame", "Marcaste $stepsWeek pasos en tu Cuaderno esta semana.",
        "Paso a paso también se llega. Sigue con el que tengas más a la mano.", 26,
    )
    val active = nb.active.size
    if (active >= 6) out += TokkipNote(
        "nb-many", "star", "Tienes $active proyectos abiertos al mismo tiempo.",
        "Escoge uno o dos para esta semana y deja los demás en pausa, sin culpa.", 30,
    )
    return out
}

/** Today's day key and the start of a day `n` days back (for the activity log). */
fun dayBefore(now: Long, n: Int): String =
    dayKey(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -n) }.timeInMillis)
