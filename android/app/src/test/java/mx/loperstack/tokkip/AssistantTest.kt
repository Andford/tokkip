package mx.loperstack.tokkip

import mx.loperstack.tokkip.assistant.Persona
import mx.loperstack.tokkip.assistant.Sentences
import mx.loperstack.tokkip.assistant.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
