package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.AppKind
import mx.loperstack.tokkip.data.Night
import mx.loperstack.tokkip.data.ScreenDay
import mx.loperstack.tokkip.data.ScreenSettings
import mx.loperstack.tokkip.data.UseEvent
import mx.loperstack.tokkip.data.UseSpan
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.doomLevel
import mx.loperstack.tokkip.data.kindOf
import mx.loperstack.tokkip.data.screenDaysJson
import mx.loperstack.tokkip.data.screenDaysOf
import mx.loperstack.tokkip.data.screenNotes
import mx.loperstack.tokkip.data.screenSettingsJson
import mx.loperstack.tokkip.data.screenSettingsOf
import mx.loperstack.tokkip.data.sessionsOf
import mx.loperstack.tokkip.data.spansOf
import mx.loperstack.tokkip.data.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScreenTimeTest {
    private fun at(h: Int, m: Int = 0, d: Int = 24) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, d, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val day = at(0)
    private val min = 60_000L

    @Test fun kinds() {
        assertEquals(AppKind.SOCIAL, kindOf("com.instagram.android", -1))
        assertEquals(AppKind.VIDEO, kindOf("com.google.android.youtube", 7))
        assertEquals(AppKind.GAMES, kindOf("com.some.game", 0))
        assertEquals(AppKind.OTHER, kindOf("com.some.thing", -1))
    }

    @Test fun eventsToSpans() {
        val ev = listOf(
            UseEvent(at(9), "com.instagram.android", true),
            UseEvent(at(9, 20), "com.whatsapp", true), // another app comes up: Instagram ends
            UseEvent(at(9, 25), "com.whatsapp", false),
            UseEvent(at(10), "com.instagram.android", true),
            UseEvent(at(10, 30), null, false), // screen off
        )
        val spans = spansOf(ev, at(12))
        assertEquals(listOf(20L, 5L, 30L), spans.map { it.ms / min })
        // Still open at "now".
        assertEquals(15L, spansOf(listOf(UseEvent(at(11, 45), "com.instagram.android", true)), at(12)).single().ms / min)
    }

    @Test fun sessionsJoinShortGaps() {
        val s = sessionsOf(listOf(UseSpan("a", at(9), at(9, 10)), UseSpan("a", at(9, 10) + 30_000, at(9, 30)), UseSpan("a", at(10), at(10, 5))))
        assertEquals(listOf(30L, 5L), s.map { it.ms / min })
    }

    @Test fun today() {
        val spans = listOf(
            UseSpan("com.instagram.android", at(8, 50), at(9, 20)),
            UseSpan("com.whatsapp", at(9, 20), at(9, 30)),
            UseSpan("launcher", at(9, 30), at(9, 40)),
            UseSpan("com.zhiliaoapp.musically", at(11, 30), at(12)),
            UseSpan("com.instagram.android", at(23, 30, 23), at(0, 10)), // from last night: only today's 10 min
        )
        val t = summarize(spans, day, at(12), ScreenSettings(), 12, { it }, { kindOf(it, -1) }, { it == "launcher" })
        assertEquals(80, t.totalMin)
        assertEquals(70, t.scrollMin)
        assertEquals("com.instagram.android", t.apps.first().pkg)
        assertEquals(40, t.apps.first().minutes)
        assertEquals(AppKind.SOCIAL, t.byKind.first().first)
        assertEquals(30, t.sessionMin)
        assertEquals("com.zhiliaoapp.musically", t.sessionApp)
        assertEquals(30, t.longestMin)
        assertEquals(10, t.hourly[8])
        assertEquals(30, t.hourly[9])
        assertEquals(10, t.lateMin)
        assertEquals(12, t.unlocks)
    }

    @Test fun levels() {
        assertEquals(0, doomLevel(20, 60, 0, 14))
        assertEquals(1, doomLevel(40, 60, 0, 14))
        assertEquals(2, doomLevel(70, 60, 0, 14))
        assertEquals(3, doomLevel(95, 60, 0, 14))
        assertEquals(3, doomLevel(10, 60, 45, 14))
        // Late at night counts more.
        assertEquals(2, doomLevel(10, 60, 12, 23))
    }

    @Test fun notes() {
        val now = at(13)
        val days = (1..7).map { b ->
            val d = dayKey(at(12, 0, 24 - b))
            ScreenDay(d, 200, if (b % 2 == 0) 120 else 30, lateMin = if (b % 2 == 0) 45 else 0)
        }
        val nights = (1..7).map { b -> Night(dayKey(at(12, 0, 25 - b)), if (b % 2 == 0) 380 else 470) }
        val moods = days.associate { it.day to if (it.scrollMin > 60) 2 else 4 }
        val ids = screenNotes(days, ScreenDay(dayKey(now), 150, 100), 60, nights, moods, now).map { it.id }
        assertTrue(ids.toString(), "screen-dizzy" in ids)
        assertTrue(ids.toString(), "screen-late" in ids)
        assertTrue(ids.toString(), "screen-sleep" in ids)
        assertTrue(ids.toString(), "screen-mood" in ids)
    }

    @Test fun json() {
        val s = ScreenSettings(45, 15, setOf(AppKind.SOCIAL, AppKind.GAMES))
        assertEquals(s, screenSettingsOf(screenSettingsJson(s)))
        val d = listOf(ScreenDay("2026-10-23", 300, 90, 20, 70))
        assertEquals(d, screenDaysOf(screenDaysJson(d)))
    }
}
