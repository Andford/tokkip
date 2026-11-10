package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.DayLog
import mx.loperstack.tokkip.data.TokkipJson
import mx.loperstack.tokkip.data.dayKey
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tokkip shared with Tokkip PC, through Tokkip Phone (data/PcTokkip.kt). */
class PcTokkipTest {
    private val data = AppData(
        species = "GATO", skin = "ENSUENO", tokkipColor = 0xFFFFFFFF, accent = 0xFFE91E63, outfit = "BEANIE",
    )

    @Test fun ourTokkipGoesOutInTheIslandsWords() {
        val o = JSONObject(TokkipJson.json(TokkipJson.lookOf(data), 42, null))
        assertEquals(1, o.getInt("v"))
        assertEquals(42L, o.getLong("changedAt"))
        val look = o.getJSONObject("look")
        assertEquals("gato", look.getString("species"))
        assertEquals("ensueno", look.getString("skin"))
        assertEquals("#FFFFFF", look.getString("body"))
        assertEquals("#E91E63", look.getString("accent"))
        assertEquals("", look.getString("ink"))
        assertEquals("beanie", look.getString("outfit"))
        assertEquals(false, o.has("feeling"))
    }

    @Test fun thePcsComesBackAsTheSameLook() {
        val shared = TokkipJson.parse(TokkipJson.json(TokkipJson.lookOf(data), 7, null))!!
        assertEquals(7L, shared.changedAt)
        assertEquals(TokkipJson.keyOf(TokkipJson.lookOf(data)), TokkipJson.keyOf(shared.look))
        assertEquals("BEANIE", TokkipJson.outfitFromPc(shared.look.outfit))
        assertNotEquals(TokkipJson.keyOf(TokkipJson.lookOf(data)), TokkipJson.keyOf(TokkipJson.lookOf(data.copy(outfit = "CROWN"))))
    }

    @Test fun anythingOddFallsBack() {
        assertNull(TokkipJson.parse(null))
        assertNull(TokkipJson.parse("nope"))
        assertNull(TokkipJson.parse("""{"v":2,"changedAt":1,"look":{}}"""))
        assertNull(TokkipJson.parse("""{"v":1,"look":{}}"""))
        val odd = TokkipJson.parse("""{"v":1,"changedAt":3,"look":{"species":"dragon","body":"red","outfit":"cape"}}""")!!
        assertEquals("conejo", odd.look.species)
        assertEquals("auto", odd.look.outfit)
        assertEquals(0L, odd.look.ink)
        assertEquals("auto", TokkipJson.outfitToPc("BUNNY"))
    }

    @Test fun todaysMoodGoesWithIt() {
        val now = System.currentTimeMillis()
        val withMood = data.copy(logs = listOf(DayLog(dayKey(now), mood = 2)))
        val f = TokkipJson.feelingJson(withMood, now)!!
        assertEquals(dayKey(now), f.getString("day"))
        assertEquals(2, f.getInt("mood"))
        assertNull(TokkipJson.feelingJson(data, now))
    }
}
