package mx.loperstack.tokkipnotas

import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.Settle
import mx.loperstack.tokkipnotas.data.SyncCrypto
import mx.loperstack.tokkipnotas.data.SyncState
import mx.loperstack.tokkipnotas.data.settle
import mx.loperstack.tokkipnotas.data.travelJson
import mx.loperstack.tokkipnotas.data.travelOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoreTest {
    private val salt = "c2FsdHNhbHRzYWx0c2FsdA=="

    @Test
    fun sameCryptoAsWindows() {
        // The vector from notas-pc/src-tauri/src/sync.rs.
        val key = SyncCrypto.derive("canción secreta", salt)
        assertEquals("d06abd18ac1ee5945d614aacf04a8bff3adf1a4d4dbf7728caac48f3e69d36f8", key.joinToString("") { "%02x".format(it) })
        val fromWindows = "RZxyKkhSCOP1y5QNjU0wbLNns4y8YzwpNEO2naw/Qfsc5f9jE5RAQp4g6aVuRN5aOiJcfliOSe52nxX4WSF8QxRAsbhsX2WPfA=="
        assertEquals("{\"id\":\"v\",\"title\":\"Hola ñ\",\"body\":\"[ ] uno\"}", SyncCrypto.open(key, fromWindows)!!.decodeToString())
        assertEquals("ccb41c9f1731c30fb44030e16847abdd466092d3093768aa37293be77eb5dfea", SyncCrypto.fingerprint("Título", "uno\ndos"))
        // NFC: a combining accent gives the same key.
        assertEquals(key.toList(), SyncCrypto.derive("canción secreta", salt).toList())
        val sealed = SyncCrypto.seal(key, "hola".toByteArray())
        assertEquals("hola", SyncCrypto.open(key, sealed)!!.decodeToString())
        assertNull(SyncCrypto.open(SyncCrypto.derive("otra frase", salt), sealed))
    }

    @Test
    fun aPageTravelsWithoutOpened() {
        val p = Page("a", "b", "Título", "idea", 0xFFA78BFA, 3, PageKind.DOC, 1.5, 10, 20, opened = 99, favorite = true, props = mapOf("status" to "done"))
        val o = travelJson(p, "[ ] uno")
        assertTrue(!o.has("opened"))
        val (back, body) = travelOf(JSONObject(o.toString()))
        assertEquals(p.copy(opened = 0), back)
        assertEquals("[ ] uno", body)
    }

    private fun page(title: String, body: String) = Page("a", null, title) to body
    private fun fp(p: Pair<Page, String>) = SyncCrypto.fingerprint(p.first.title, p.second)

    @Test
    fun settling() {
        val base = page("Lista", "pan")
        val noCopy: (Page, String) -> Unit = { _, _ -> error("no copy expected") }
        // Not waiting here: newer comes in, older or the same is skipped.
        assertEquals(Settle.Take, settle("a", 5, base, SyncState(4, false, null), null, noCopy))
        assertEquals(Settle.Skip, settle("a", 4, base, SyncState(4, false, null), null, noCopy))
        assertEquals(Settle.Take, settle("a", 1, base, null, null, noCopy))

        // Only the rest changed here (favourite), their words are newer → take theirs.
        val theirs = page("Lista", "pan\nleche")
        val mineMeta = base.first.copy(favorite = true) to base.second
        assertEquals(Settle.Take, settle("a", 10, theirs, SyncState(5, true, fp(base)), mineMeta, noCopy))
        // …our favourite is newer → keep ours with their words.
        val keep = settle("a", 3, theirs, SyncState(5, true, fp(base)), mineMeta, noCopy) as Settle.Keep
        assertEquals("pan\nleche", keep.body)
        assertTrue(keep.page!!.favorite)
        assertEquals(5L, keep.ver)
        assertEquals(fp(theirs), keep.base)

        // Words here, only the rest there (moved), theirs newer → their place, our words, a newer version.
        val mineWords = page("Lista", "pan\nsal")
        val moved = base.first.copy(ord = 9.0) to base.second
        val k2 = settle("a", 10, moved, SyncState(5, true, fp(base)), mineWords, noCopy) as Settle.Keep
        assertEquals(9.0, k2.page!!.ord, 0.0)
        assertEquals("pan\nsal", k2.body)
        assertEquals(11L, k2.ver)

        // Words on both sides: the newer wins, the other is kept as a copy.
        var copied: String? = null
        assertEquals(Settle.Take, settle("a", 10, theirs, SyncState(5, true, fp(base)), mineWords) { _, b -> copied = b })
        assertEquals("pan\nsal", copied)
        copied = null
        val k3 = settle("a", 3, theirs, SyncState(5, true, fp(base)), mineWords) { _, b -> copied = b }
        assertTrue(k3 is Settle.Keep)
        assertEquals("pan\nleche", copied)

        // Deleted here, written there: the writing wins. Written here, deleted there: ours wins.
        assertEquals(Settle.Take, settle("a", 10, theirs, SyncState(12, true, fp(base)), null, noCopy))
        val k4 = settle("a", 10, null, SyncState(5, true, fp(base)), mineWords, noCopy) as Settle.Keep
        assertEquals(11L, k4.ver)
        // Deleted there, only the rest changed here: the delete wins.
        assertEquals(Settle.Take, settle("a", 3, null, SyncState(5, true, fp(base)), mineMeta, noCopy))

        // The same words on both sides: no copy.
        assertEquals(Settle.Take, settle("a", 10, mineWords, SyncState(5, true, null), mineWords, noCopy))
        // The Bandeja is never copied.
        assertEquals(Settle.Take, settle("inbox", 10, theirs, SyncState(5, true, null), mineWords, noCopy))
    }
}
