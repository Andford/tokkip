package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.PhoneHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How the phone feels, from Tokkip Health (data/PhoneHealth.kt). */
class PhoneHealthTest {
    private val ok = """{"app":"tokkip-health","version":1,"seq":3,"at":1763740000000,"score":70,"mood":"calm","kind":"clean","message":"Liberé 1.2 GB en tu teléfono","bytes":1200000000}"""

    @Test fun readsTokkipHealthsNote() {
        val n = PhoneHealth.parse(ok)!!
        assertEquals(3L, n.seq)
        assertEquals(70, n.score)
        assertEquals("calm", n.mood)
        assertEquals("clean", n.kind)
        assertEquals(1_200_000_000L, n.bytes)
    }

    @Test fun ignoresAnythingElse() {
        assertNull(PhoneHealth.parse(null))
        assertNull(PhoneHealth.parse("not json"))
        assertNull(PhoneHealth.parse(ok.replace("tokkip-health", "someone-else")))
        assertNull(PhoneHealth.parse(ok.replace("\"score\":70", "\"score\":170")))
        assertNull(PhoneHealth.parse(ok.replace("\"calm\"", "\"angry\"")))
        assertNull(PhoneHealth.parse(ok.replace("Liberé 1.2 GB en tu teléfono", "")))
    }
}
