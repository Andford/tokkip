// Where the Cuaderno lives: a small SQLite database on the phone
// (cuaderno.db), apart from the rest of Tokkip's settings. Only the index —
// projects, each note's title, preview and step counts, and a line per day —
// stays in memory; a note's text is read when you open it and written a
// moment after you stop typing, on a thread of its own. So hundreds of notes
// cost the app nothing until you open one.

package mx.loperstack.tokkip.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

private class NotebookHelper(context: Context) : SQLiteOpenHelper(context, "cuaderno.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE projects (id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, shape INTEGER NOT NULL,
               icon TEXT NOT NULL, why TEXT NOT NULL DEFAULT '', due TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL, updated INTEGER NOT NULL,
               done_at INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, ord INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE notes (id TEXT PRIMARY KEY, project TEXT, title TEXT NOT NULL DEFAULT '', body TEXT NOT NULL DEFAULT '',
               preview TEXT NOT NULL DEFAULT '', steps INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, next TEXT NOT NULL DEFAULT '',
               pinned INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, updated INTEGER NOT NULL, opened INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL("CREATE INDEX notes_project ON notes(project)")
        db.execSQL("CREATE TABLE days (day TEXT PRIMARY KEY, edits INTEGER NOT NULL DEFAULT 0, steps INTEGER NOT NULL DEFAULT 0, finished INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
}

object Notebook {
    private var helper: NotebookHelper? = null
    private val db get() = helper!!.writableDatabase
    /** Writes go one after another on their own thread, never on the screen's. */
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cuaderno").apply { isDaemon = true } }
    private val _index = MutableStateFlow(NotebookIndex())
    val index: StateFlow<NotebookIndex> = _index.asStateFlow()

    fun init(context: Context) {
        if (helper != null) return
        helper = NotebookHelper(context.applicationContext)
        runCatching { _index.value = load() }
    }

    private fun load(): NotebookIndex {
        val projects = ArrayList<Project>()
        db.rawQuery("SELECT id,name,color,shape,icon,why,due,created,updated,done_at,archived,ord FROM projects", null).use { c ->
            while (c.moveToNext()) projects += Project(
                c.getString(0), c.getString(1), c.getLong(2), c.getInt(3), c.getString(4), c.getString(5), c.getString(6),
                c.getLong(7), c.getLong(8), c.getLong(9), c.getInt(10) != 0, c.getInt(11),
            )
        }
        val notes = ArrayList<NoteHead>()
        db.rawQuery("SELECT id,project,title,preview,steps,done,pinned,created,updated,opened,next FROM notes", null).use { c ->
            while (c.moveToNext()) notes += NoteHead(
                c.getString(0), if (c.isNull(1)) null else c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5),
                c.getInt(6) != 0, c.getLong(7), c.getLong(8), c.getLong(9), c.getString(10),
            )
        }
        val days = ArrayList<NotebookDay>()
        db.rawQuery("SELECT day,edits,steps,finished FROM days ORDER BY day DESC LIMIT 400", null).use { c ->
            while (c.moveToNext()) days += NotebookDay(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3))
        }
        return NotebookIndex(projects, notes.sortedByDescending { it.updated }, days)
    }

    private fun write(block: SQLiteDatabase.() -> Unit) {
        io.execute { runCatching { db.block() } }
    }

    private fun bumpDay(edits: Int = 0, steps: Int = 0, finished: Int = 0) {
        val day = dayKey(System.currentTimeMillis())
        _index.value = _index.value.let { ix ->
            val d = ix.days.firstOrNull { it.day == day } ?: NotebookDay(day, 0, 0, 0)
            val nd = d.copy(edits = d.edits + edits, steps = d.steps + steps, finished = d.finished + finished)
            ix.copy(days = listOf(nd) + ix.days.filter { it.day != day })
        }
        write {
            execSQL("INSERT OR IGNORE INTO days(day) VALUES(?)", arrayOf(day))
            execSQL("UPDATE days SET edits=edits+?, steps=steps+?, finished=finished+? WHERE day=?", arrayOf(edits, steps, finished, day))
        }
    }

    private fun setNote(h: NoteHead) {
        _index.value = _index.value.let { ix -> ix.copy(notes = (listOf(h) + ix.notes.filter { it.id != h.id }).sortedByDescending { it.updated }) }
    }

    fun note(id: String): NoteHead? = _index.value.notes.firstOrNull { it.id == id }
    fun project(id: String?): Project? = id?.let { i -> _index.value.projects.firstOrNull { it.id == i } }

    // ── Notes ─────────────────────────────────────────────────────────────────

    /** A new note (in a project, or in the Bandeja); its id. */
    fun newNote(project: String?, title: String = "", body: String = ""): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val f = factsOf(body)
        setNote(NoteHead(id, project, title, f.preview, f.steps, f.done, false, now, now, now, f.next))
        write {
            insert("notes", null, ContentValues().apply {
                put("id", id); put("project", project); put("title", title); put("body", body); put("preview", f.preview)
                put("steps", f.steps); put("done", f.done); put("next", f.next); put("created", now); put("updated", now); put("opened", now)
            })
        }
        project?.let { touchProject(it) }
        return id
    }

    /** A quick capture: the first line is the title, it waits in the Bandeja. */
    fun capture(text: String, project: String? = null): String? {
        if (text.isBlank()) return null
        val (title, body) = captureOf(text)
        bumpDay(edits = 1)
        return newNote(project, title, body)
    }

    /** The note's text (blocking: call off the screen's thread). */
    fun body(id: String): String = runCatching {
        // Waits for pending writes, so you never read an older text.
        io.submit<String> {
            db.rawQuery("SELECT body FROM notes WHERE id=?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
        }.get()
    }.getOrDefault("")

    fun save(id: String, title: String, body: String) {
        val old = note(id) ?: return
        val f = factsOf(body)
        if (old.title == title && old.preview == f.preview && old.steps == f.steps && old.done == f.done && old.next == f.next) {
            // Same facts: only the text may have changed (beyond the preview).
            write { execSQL("UPDATE notes SET body=? WHERE id=?", arrayOf(body, id)) }
            return
        }
        val now = System.currentTimeMillis()
        val ticked = (f.done - old.done).coerceAtLeast(0)
        setNote(old.copy(title = title, preview = f.preview, steps = f.steps, done = f.done, next = f.next, updated = now))
        bumpDay(edits = if (old.updated < now - 60_000 || old.title != title || ticked == 0) 1 else 0, steps = ticked)
        write {
            execSQL(
                "UPDATE notes SET title=?, body=?, preview=?, steps=?, done=?, next=?, updated=? WHERE id=?",
                arrayOf(title, body, f.preview, f.steps, f.done, f.next, now, id),
            )
        }
        old.project?.let { touchProject(it) }
    }

    /** Ticks the first step still to do, without opening the note. The step's text, or null. */
    fun tickNext(id: String): String? {
        val h = note(id) ?: return null
        val blocks = blocksOf(body(id)).toMutableList()
        val i = blocks.indexOfFirst { it.kind == BlockKind.TODO && it.text.isNotBlank() }
        if (i < 0) return null
        blocks[i] = blocks[i].copy(kind = BlockKind.DONE)
        save(id, h.title, bodyOf(blocks))
        return blocks[i].text
    }

    fun opened(id: String) {
        val h = note(id) ?: return
        val now = System.currentTimeMillis()
        _index.value = _index.value.let { ix -> ix.copy(notes = ix.notes.map { if (it.id == id) h.copy(opened = now) else it }) }
        write { execSQL("UPDATE notes SET opened=? WHERE id=?", arrayOf(now, id)) }
    }

    fun move(id: String, project: String?) {
        val h = note(id) ?: return
        setNote(h.copy(project = project))
        write { execSQL("UPDATE notes SET project=? WHERE id=?", arrayOf(project, id)) }
        project?.let { touchProject(it) }
    }

    fun pin(id: String, on: Boolean) {
        val h = note(id) ?: return
        _index.value = _index.value.let { ix -> ix.copy(notes = ix.notes.map { if (it.id == id) h.copy(pinned = on) else it }) }
        write { execSQL("UPDATE notes SET pinned=? WHERE id=?", arrayOf(if (on) 1 else 0, id)) }
    }

    fun delete(id: String) {
        _index.value = _index.value.let { ix -> ix.copy(notes = ix.notes.filter { it.id != id }) }
        write { delete("notes", "id=?", arrayOf(id)) }
    }

    /** Notes matching `query`, best first, with a piece of text around it. Blocking. */
    fun search(query: String): List<Pair<NoteHead, String>> = runCatching {
        if (query.isBlank()) return emptyList()
        val heads = _index.value.notes.associateBy { it.id }
        io.submit<List<Pair<NoteHead, String>>> {
            val out = ArrayList<Triple<NoteHead, String, Int>>()
            db.rawQuery("SELECT id,title,body FROM notes", null).use { c ->
                while (c.moveToNext()) {
                    val h = heads[c.getString(0)] ?: continue
                    val body = c.getString(2)
                    val s = searchScore(query, c.getString(1), body)
                    if (s > 0) out += Triple(h, snippet(query, body.lines().joinToString(" ") { blocksOf(it).first().text }), s)
                }
            }
            out.sortedWith(compareByDescending<Triple<NoteHead, String, Int>> { it.third }.thenByDescending { it.first.updated }).take(40).map { it.first to it.second }
        }.get()
    }.getOrDefault(emptyList())

    // ── Projects ──────────────────────────────────────────────────────────────

    private fun touchProject(id: String) {
        val now = System.currentTimeMillis()
        _index.value = _index.value.let { ix -> ix.copy(projects = ix.projects.map { if (it.id == id) it.copy(updated = now) else it }) }
        write { execSQL("UPDATE projects SET updated=? WHERE id=?", arrayOf(now, id)) }
    }

    fun saveProject(p: Project) {
        _index.value = _index.value.let { ix -> ix.copy(projects = listOf(p) + ix.projects.filter { it.id != p.id }) }
        write {
            insertWithOnConflict("projects", null, ContentValues().apply {
                put("id", p.id); put("name", p.name); put("color", p.color); put("shape", p.shape); put("icon", p.icon); put("why", p.why)
                put("due", p.due); put("created", p.created); put("updated", p.updated); put("done_at", p.doneAt)
                put("archived", if (p.archived) 1 else 0); put("ord", p.order)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun newProject(name: String, color: Long, shape: Int, icon: String, why: String): Project {
        val now = System.currentTimeMillis()
        val order = (_index.value.projects.minOfOrNull { it.order } ?: 0) - 1
        return Project(UUID.randomUUID().toString(), name.trim(), color, shape, icon, why.trim(), "", now, now, order = order).also { saveProject(it) }
    }

    /** Done (it counts for Tokkip once), or back to going. */
    fun finishProject(id: String, done: Boolean) {
        val p = project(id) ?: return
        if (done == (p.doneAt > 0)) return
        val now = System.currentTimeMillis()
        saveProject(p.copy(doneAt = if (done) now else 0, updated = now))
        if (done) bumpDay(finished = 1)
    }

    /** Deletes the project; its notes go back to the Bandeja (or go too). */
    fun deleteProject(id: String, withNotes: Boolean) {
        _index.value = _index.value.let { ix ->
            ix.copy(
                projects = ix.projects.filter { it.id != id },
                notes = if (withNotes) ix.notes.filter { it.project != id } else ix.notes.map { if (it.project == id) it.copy(project = null) else it },
            )
        }
        write {
            delete("projects", "id=?", arrayOf(id))
            if (withNotes) delete("notes", "project=?", arrayOf(id))
            else execSQL("UPDATE notes SET project=NULL WHERE project=?", arrayOf(id))
        }
    }

    // ── Copy (.zip) ───────────────────────────────────────────────────────────

    /** Everything, for the backup. Blocking. */
    fun exportJson(): JSONObject = io.submit<JSONObject> {
        val root = JSONObject().put("version", 1)
        root.put("projects", JSONArray().apply {
            db.rawQuery("SELECT * FROM projects", null).use { c -> while (c.moveToNext()) put(rowJson(c)) }
        })
        root.put("notes", JSONArray().apply {
            db.rawQuery("SELECT * FROM notes", null).use { c -> while (c.moveToNext()) put(rowJson(c)) }
        })
        root.put("days", JSONArray().apply {
            db.rawQuery("SELECT * FROM days", null).use { c -> while (c.moveToNext()) put(rowJson(c)) }
        })
    }.get()

    private fun rowJson(c: android.database.Cursor) = JSONObject().apply {
        for (i in 0 until c.columnCount) when (c.getType(i)) {
            android.database.Cursor.FIELD_TYPE_INTEGER -> put(c.getColumnName(i), c.getLong(i))
            android.database.Cursor.FIELD_TYPE_STRING -> put(c.getColumnName(i), c.getString(i))
            else -> {}
        }
    }

    /** Replaces the Cuaderno with a copy's. Blocking. */
    fun importJson(root: JSONObject) {
        io.submit {
            db.beginTransaction()
            try {
                for (t in listOf("projects", "notes", "days")) {
                    db.delete(t, null, null)
                    val a = root.optJSONArray(t) ?: continue
                    for (i in 0 until a.length()) {
                        val o = a.optJSONObject(i) ?: continue
                        val cv = ContentValues()
                        for (k in o.keys()) when (val v = o.get(k)) {
                            is String -> cv.put(k, v)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Number -> cv.put(k, v.toLong())
                        }
                        db.insertWithOnConflict(t, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.get()
        _index.value = io.submit<NotebookIndex> { load() }.get()
    }

    /** Empties it (Empezar de cero, when Android won't clear the app). */
    fun wipe() {
        runCatching { importJson(JSONObject()) }
    }
}
