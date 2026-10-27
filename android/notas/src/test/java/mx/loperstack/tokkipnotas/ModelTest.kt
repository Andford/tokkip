package mx.loperstack.tokkipnotas

import mx.loperstack.tokkipnotas.data.BlockKind
import mx.loperstack.tokkipnotas.data.DAY_MS
import mx.loperstack.tokkipnotas.data.INBOX_ID
import mx.loperstack.tokkipnotas.data.Index
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.PropType
import mx.loperstack.tokkipnotas.data.TEMPLATES
import mx.loperstack.tokkipnotas.data.between
import mx.loperstack.tokkipnotas.data.blocksOf
import mx.loperstack.tokkipnotas.data.board
import mx.loperstack.tokkipnotas.data.bodyOf
import mx.loperstack.tokkipnotas.data.build
import mx.loperstack.tokkipnotas.data.dayKey
import mx.loperstack.tokkipnotas.data.defaultSchema
import mx.loperstack.tokkipnotas.data.display
import mx.loperstack.tokkipnotas.data.dueSoon
import mx.loperstack.tokkipnotas.data.factsOf
import mx.loperstack.tokkipnotas.data.flatten
import mx.loperstack.tokkipnotas.data.fromTokkip
import mx.loperstack.tokkipnotas.data.moveCard
import mx.loperstack.tokkipnotas.data.numberAt
import mx.loperstack.tokkipnotas.data.rows
import mx.loperstack.tokkipnotas.data.schemaJson
import mx.loperstack.tokkipnotas.data.schemaOf
import mx.loperstack.tokkipnotas.data.searchScore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private val now = 1_790_000_000_000L

    @Test fun blocksRoundTrip() {
        val body = "# Uno\n## Dos\n• bala\n1. primero\n2. segundo\n[ ] paso\n[x] hecho\n> cita\n! ojo\n---\n@@abc\ntexto"
        val b = blocksOf(body)
        assertEquals(
            listOf(BlockKind.H1, BlockKind.H2, BlockKind.BULLET, BlockKind.NUMBER, BlockKind.NUMBER, BlockKind.TODO, BlockKind.DONE,
                BlockKind.QUOTE, BlockKind.CALLOUT, BlockKind.DIVIDER, BlockKind.PAGE, BlockKind.TEXT),
            b.map { it.kind },
        )
        assertEquals("abc", b[10].text)
        assertEquals(2, numberAt(b.map { it.kind }, 4))
        // Numbers are written as "1. " and counted on screen.
        assertEquals(body.replace("2. segundo", "1. segundo"), bodyOf(b))
        val f = factsOf(body)
        assertEquals(2, f.steps); assertEquals(1, f.done); assertEquals(listOf("abc"), f.links)
    }

    private fun card(id: String, parent: String, status: String?, ord: Double, date: String? = null) =
        Page(id, parent, id, ord = ord, props = listOfNotNull(status?.let { "status" to it }, date?.let { "date" to it }).toMap())

    @Test fun boardLanesAndMoves() {
        val c = Page("c", null, "Tablero", kind = PageKind.COLLECTION, schema = defaultSchema())
        val ix = Index(listOf(c, card("a", "c", "todo", 1.0), card("b", "c", "todo", 2.0), card("d", "c", "done", 1.0), card("x", "c", null, 1.0)))
        val lanes = ix.board(c)
        assertEquals(listOf(null, "todo", "doing", "done"), lanes.map { it.opt?.id })
        assertEquals(listOf("a", "b"), lanes[1].cards.map { it.id })
        val g = c.schema!!.group!!
        // "b" to the top of Haciendo… and "a" between nothing and "b" in Por hacer.
        val moved = moveCard(ix.page("b")!!, g, lanes[2], 0)
        assertEquals("doing", moved.props["status"])
        val first = moveCard(ix.page("a")!!, g, lanes[3], 0)
        assertTrue(first.ord < 1.0)
        assertEquals(1.5, between(1.0, 2.0), 0.0)
        assertNull(moveCard(ix.page("a")!!, g, lanes[0], 0).props["status"])
    }

    @Test fun tableSortsAndShows() {
        val s = defaultSchema("table").copy(sortBy = "status")
        val c = Page("c", null, "T", kind = PageKind.COLLECTION, schema = s)
        val ix = Index(listOf(c, card("a", "c", "done", 1.0), card("b", "c", "todo", 2.0)))
        assertEquals(listOf("b", "a"), ix.rows(c).map { it.id })
        assertEquals(listOf("a", "b"), ix.rows(c.copy(schema = s.copy(sortDesc = true))).map { it.id })
        assertEquals("Hecho", display(s.props[0], "done"))
        assertEquals("", display(s.props[0], null))
        assertEquals(s, schemaOf(schemaJson(s)))
    }

    @Test fun dueSoonSkipsDone() {
        val c = Page("c", null, "T", kind = PageKind.COLLECTION, schema = defaultSchema())
        val today = dayKey(now)
        val ix = Index(
            listOf(
                c, card("late", "c", "todo", 1.0, dayKey(now - 3 * DAY_MS)), card("today", "c", "doing", 2.0, today),
                card("done", "c", "done", 3.0, today), card("later", "c", "todo", 4.0, dayKey(now + 9 * DAY_MS)),
            ),
        )
        assertEquals(listOf("late", "today"), ix.dueSoon(now).map { it.card.id })
    }

    @Test fun treePathsAndTrash() {
        val ix = Index(
            listOf(
                Page("a", null, "A"), Page("b", "a", "B"), Page("c", "b", "C"), Page(INBOX_ID, null, "Bandeja"),
                Page("t", "a", "T", trashed = 5), Page("t2", "t", "T2", trashed = 5),
            ),
        )
        assertEquals(listOf("a", "b"), ix.path("c").map { it.id })
        assertTrue(ix.isInside("c", "a"))
        assertFalse(ix.isInside("a", "c"))
        assertEquals(listOf("a"), ix.roots.map { it.id })
        assertEquals(listOf("b"), ix.children("a").map { it.id })
        // The Papelera shows what you threw away, not what went with it.
        assertEquals(listOf("t"), ix.trash.map { it.id })
    }

    @Test fun templatesBuildTrees() {
        for (t in TEMPLATES) {
            val d = build(t, null, now)
            assertTrue(t.id, d.flatten().isNotEmpty())
        }
        val project = build(TEMPLATES.first { it.id == "project" }, null, now).flatten()
        assertEquals(3, project.size)
        val board = project[1].first
        assertEquals(PageKind.COLLECTION, board.kind)
        assertTrue(project[0].second.contains("@@${board.id}"))
        val reading = build(TEMPLATES.first { it.id == "reading" }, null, now).page
        assertEquals(PropType.NUMBER, reading.schema!!.props[2].type)
    }

    @Test fun searchIgnoresAccents() {
        assertTrue(searchScore("cancion", "Canción", "") > searchScore("cancion", "x", "canción"))
        assertEquals(0, searchScore("azul rojo", "azul", ""))
    }

    @Test fun bringsTokkipsCuaderno() {
        val root = JSONObject()
            .put("projects", JSONArray().put(JSONObject().put("id", "p1").put("name", "Cuarto").put("color", 0xFF60A5FA).put("shape", 2).put("icon", "home").put("why", "Calma").put("done_at", 0)))
            .put("notes", JSONArray()
                .put(JSONObject().put("id", "n1").put("project", "p1").put("title", "Pasos").put("body", "[ ] pintar\n[x] medir").put("updated", 5))
                .put(JSONObject().put("id", "n2").put("title", "Idea").put("body", "algo").put("pinned", 1)))
        val pages = fromTokkip(root)
        assertEquals(3, pages.size)
        val (proj, why) = pages[0]
        assertEquals("tk-p1", proj.id); assertEquals("! Calma", why)
        assertEquals("tk-p1", pages[1].first.parent)
        assertEquals(2, pages[1].first.steps)
        assertEquals(INBOX_ID, pages[2].first.parent)
        assertTrue(pages[2].first.favorite)
    }

    @Test
    fun secondBrain() {
        val all = build(TEMPLATES.first { it.id == "brain" }, null, 1).flatten()
        assertEquals(listOf("Segundo cerebro", "Proyectos", "Mi primer proyecto", "Áreas", "Trabajo"), all.take(5).map { it.first.title })
        val top = all.first()
        for ((kid, _) in all.filter { it.first.parent == top.first.id }) assertTrue(top.second.contains("@@${kid.id}"))
        assertEquals("board", all.first { it.first.title == "Proyectos" }.first.schema?.view)
    }
}
