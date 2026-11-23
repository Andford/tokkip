package mx.loperstack.tokkipnotas

import mx.loperstack.tokkipnotas.data.toggleStep
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStepTest {
    @Test fun ticksAndUnticksOnlySteps() {
        val body = "# Súper\n[ ] Leche\n[x] Pan\nNota"
        assertEquals("# Súper\n[x] Leche\n[x] Pan\nNota", toggleStep(body, 1))
        assertEquals("# Súper\n[ ] Leche\n[ ] Pan\nNota", toggleStep(body, 2))
        assertEquals(body, toggleStep(body, 0))
        assertEquals(body, toggleStep(body, 3))
        assertEquals(body, toggleStep(body, 9))
    }

    @Test fun leavesDrawingsAlone() {
        val ink = "{\"v\":1,\"strokes\":[]}"
        assertEquals(ink, toggleStep(ink, 0))
    }
}
