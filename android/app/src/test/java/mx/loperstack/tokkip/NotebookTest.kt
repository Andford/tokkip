package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.BlockKind
import mx.loperstack.tokkip.data.NoteHead
import mx.loperstack.tokkip.data.NotebookIndex
import mx.loperstack.tokkip.data.Project
import mx.loperstack.tokkip.data.blocksOf
import mx.loperstack.tokkip.data.bodyOf
import mx.loperstack.tokkip.data.captureOf
import mx.loperstack.tokkip.data.factsOf
import mx.loperstack.tokkip.data.halfDone
import mx.loperstack.tokkip.data.notebookNotes
import mx.loperstack.tokkip.data.progress
import mx.loperstack.tokkip.data.searchScore
import mx.loperstack.tokkip.data.snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookTest {
    private val now = 1_790_000_000_000L
    private val day = 86_400_000L

    @Test fun bodyRoundTrips() {
        val body = "# Plan\nAlgo de texto\n• una idea\n[ ] comprar pintura\n[x] medir la pared"
        val b = blocksOf(body)
        assertEquals(listOf(BlockKind.HEADING, BlockKind.TEXT, BlockKind.BULLET, BlockKind.TODO, BlockKind.DONE), b.map { it.kind })
        assertEquals("comprar pintura", b[3].text)
        assertEquals(body, bodyOf(b))
        assertEquals(BlockKind.BULLET, blocksOf("- guion")[0].kind)
        assertEquals(1, blocksOf("").size)
    }

    @Test fun factsCountSteps() {
        val f = factsOf("Intro\n[x] uno\n[ ] dos\n[ ] tres")
        assertEquals(3, f.steps)
        assertEquals(1, f.done)
        assertEquals("dos", f.next)
        assertTrue(f.preview.startsWith("Intro · uno"))
    }

    @Test fun captureSplitsTitle() {
        assertEquals("Idea app" to "con detalles", captureOf("Idea app\ncon detalles"))
        val long = "a".repeat(80)
        assertEquals("" to long, captureOf(long))
    }

    @Test fun searchIgnoresAccents() {
        assertTrue(searchScore("cancion", "Canción nueva", "") > searchScore("cancion", "Otra", "una canción"))
        assertEquals(0, searchScore("pintura azul", "Pintura", "roja"))
        assertTrue(searchScore("pintura roja", "Pintura", "roja") > 0)
        assertTrue(snippet("pared", "Hay que medir la pared antes de comprar").contains("pared"))
    }

    private fun note(id: String, project: String?, steps: Int, done: Int, updated: Long, next: String = if (done < steps) "paso" else "") =
        NoteHead(id, project, id, "", steps, done, false, updated, updated, updated, next)

    @Test fun progressAndHalfDone() {
        val p = Project("p", "Cuarto", 0xFF60A5FA, 0, "home", created = now - 20 * day, updated = now - 20 * day)
        val ix = NotebookIndex(
            listOf(p),
            listOf(note("a", "p", 4, 1, now - day), note("b", "p", 2, 2, now - 2 * day), note("c", null, 0, 0, now - 40 * day)),
        )
        val pr = ix.progress(p)
        assertEquals(6, pr.steps)
        assertEquals(3, pr.done)
        assertEquals("a", pr.next?.id)
        // Steps left first, then what you opened lately; an old note stays out.
        assertEquals(listOf("a", "b"), ix.halfDone(now).map { it.id })
    }

    @Test fun tokkipNoticesStaleAndInbox() {
        val p = Project("p", "Cuarto", 0xFF60A5FA, 0, "home", created = now - 30 * day, updated = now - 20 * day)
        val notes = listOf(note("a", "p", 3, 1, now - 10 * day)) + List(6) { note("i$it", null, 0, 0, now) }
        val ids = notebookNotes(NotebookIndex(listOf(p), notes), now).map { it.id }
        assertTrue("nb-inbox" in ids)
        assertTrue("nb-stale" in ids)
    }
}
