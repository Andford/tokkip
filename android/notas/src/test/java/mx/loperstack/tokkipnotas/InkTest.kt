package mx.loperstack.tokkipnotas

import mx.loperstack.tokkipnotas.data.BlockKind
import mx.loperstack.tokkipnotas.data.Drawing
import mx.loperstack.tokkipnotas.data.INK_EMPTY
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.TEMPLATES
import mx.loperstack.tokkipnotas.data.blocksOf
import mx.loperstack.tokkipnotas.data.build
import mx.loperstack.tokkipnotas.data.drawingJson
import mx.loperstack.tokkipnotas.data.drawingOf
import mx.loperstack.tokkipnotas.data.factsOf
import mx.loperstack.tokkipnotas.data.lowest
import mx.loperstack.tokkipnotas.data.plainOf
import mx.loperstack.tokkipnotas.data.strokesHit
import mx.loperstack.tokkipnotas.data.widthAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkTest {
    // The same drawing as notas-pc/tests/ink.test.ts.
    private val json = """{"v":1,"bg":"grid","h":900,"s":[{"k":"pen","c":0,"w":3,"p":[100,100,50,300,100,60]},{"k":"hl","c":4294829127,"w":18,"p":[100,600,50,900,600,50]}]}"""

    @Test
    fun sharedFormat() {
        val d = drawingOf(json)
        assertEquals("grid", d.bg)
        assertEquals(900f, d.h)
        assertEquals(2, d.strokes.size)
        assertEquals("hl", d.strokes[1].kind)
        assertEquals(4294829127L, d.strokes[1].color)
        assertEquals(d, drawingOf(drawingJson(d)))
        assertEquals(Drawing(), drawingOf("basura"))
        assertEquals(300f, lowest(d))
        assertTrue(widthAt(4f, 100) > widthAt(4f, 10))
    }

    @Test
    fun eraser() {
        val d = drawingOf(json)
        assertEquals(setOf(0), strokesHit(d, 100f, 50f, 5f))
        assertEquals(setOf(1), strokesHit(d, 300f, 300f, 5f))
        assertEquals(emptySet<Int>(), strokesHit(d, 800f, 800f, 5f))
    }

    @Test
    fun drawingsStayOutOfText() {
        val body = "Hola\n%%$json\n[ ] algo"
        assertEquals(BlockKind.DRAW, blocksOf(body)[1].kind)
        assertEquals("Hola · algo", factsOf(body).preview)
        assertEquals("Hola\nalgo", plainOf(body))
        assertEquals("", factsOf(INK_EMPTY).preview)
        assertEquals("", plainOf(INK_EMPTY))
        val page = build(TEMPLATES.first { it.id == "ink" }, null, 1)
        assertEquals(PageKind.INK, page.page.kind)
        assertEquals("lines", drawingOf(page.body).bg)
    }
}
