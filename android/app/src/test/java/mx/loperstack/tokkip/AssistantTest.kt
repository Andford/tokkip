package mx.loperstack.tokkip

import mx.loperstack.tokkip.assistant.Persona
import mx.loperstack.tokkip.assistant.Sentences
import mx.loperstack.tokkip.assistant.TokkipTools
import mx.loperstack.tokkip.assistant.Voice
import mx.loperstack.tokkip.wellness.TalkReply
import mx.loperstack.tokkip.wellness.TalkWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AssistantTest {
    @Test fun speaks_sentence_by_sentence_as_the_answer_grows() {
        val s = Sentences()
        assertEquals(emptyList<String>(), s.feed("Hola, Andy", done = false))
        assertEquals(listOf("Hola, Andy."), s.feed("Hola, Andy. Primero hierve", done = false))
        assertEquals(emptyList<String>(), s.feed("Hola, Andy. Primero hierve el agua", done = false))
        assertEquals(listOf("Primero hierve el agua un minuto."), s.feed("Hola, Andy. Primero hierve el agua un minuto. Lu", done = false))
        assertEquals(listOf("Luego déjala enfriar"), s.feed("Hola, Andy. Primero hierve el agua un minuto. Luego déjala enfriar", done = true))
    }

    @Test fun reads_words_not_code() {
        assertEquals("Corre esto: y listo.", Voice.speakable("Corre esto: ```sudo apt update``` y listo."))
        assertEquals("Usa ip a para verlo.", Voice.speakable("Usa **ip a** para verlo.").replace("  ", " "))
    }

    @Test fun notices_when_someone_may_be_in_danger() {
        assertTrue(Persona.isCrisis("Ya no quiero vivir"))
        assertTrue(Persona.isCrisis("pienso en el SUICIDIO"))
        assertTrue(Persona.isCrisis("quiero hacerme daño"))
        assertFalse(Persona.isCrisis("¿Cómo potabilizo agua?"))
        assertFalse(Persona.isCrisis("el servidor se murió otra vez"))
    }

    @Test fun talks_like_a_friend_not_a_sweetheart() {
        assertEquals("¡Hola! ¿Cómo estás?", Persona.clean("¡Hola, mi amor! ¿Cómo estás?"))
        assertEquals("Claro. Primero hierve el agua.", Persona.clean("Claro, cariño. Primero hierve el agua."))
        assertEquals("Ok. tranquilo.", Persona.clean("Ok. Mi amor, tranquilo."))
        assertEquals("¿Qué es el amor?", Persona.clean("¿Qué es el amor?"))
        assertEquals("Cuida tu vida, Andy.", Persona.clean("Cuida tu vida, Andy."))
    }

    @Test
    fun alarmDays() {
        assertEquals(emptySet<Int>(), TokkipTools.daysOf(""))
        assertEquals((1..7).toSet(), TokkipTools.daysOf("Todos los días"))
        assertEquals(setOf(Calendar.MONDAY, Calendar.WEDNESDAY), TokkipTools.daysOf("lunes, miércoles"))
        assertEquals(setOf(Calendar.SATURDAY, Calendar.SUNDAY), TokkipTools.daysOf("fin de semana"))
        assertEquals(5, TokkipTools.daysOf("entre semana").size)
        assertEquals(6 * 60 + 30, TokkipTools.minuteOf("06:30"))
        assertEquals(18 * 60, TokkipTools.minuteOf("18"))
        assertEquals(null, TokkipTools.minuteOf("25:00"))
    }

    @Test
    fun watchTalkWire() {
        val r = TalkReply("Hola, Andy", done = true, crisis = true, where = "watch")
        assertEquals(r, TalkWire.replyOf(TalkWire.text(r)))
        val samples = FloatArray(TalkWire.PIECE + 10) { if (it % 2 == 0) 0.5f else -2f }
        val pieces = TalkWire.audio(samples, 22050, 1.1f)
        assertEquals(2, pieces.size)
        val a = TalkWire.audioOf(pieces[1])!!
        assertEquals(22050, a.rate)
        assertEquals(10, a.pcm.size)
        assertEquals((0.5f * Short.MAX_VALUE).toInt().toShort(), a.pcm[0])
        assertEquals((-Short.MAX_VALUE).toShort(), a.pcm[1])
        assertEquals(null, TalkWire.audioOf(ByteArray(3)))
    }
}
