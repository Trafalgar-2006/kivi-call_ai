package dev.callai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val live = "models/gemini-3.8-live-extended-thinking"

    @Test fun `a retired live model moves to the newest of the same kind`() {
        val names = listOf("models/gemini-3.5-flash-lite", "models/gemini-4.1-live-extended-thinking", "models/gemini-4.0-live-extended-thinking",
            "models/gemini-4.2-live-preview", "models/gemini-5.0-flash")
        assertEquals("models/gemini-4.1-live-extended-thinking", bestModel(names, live))
    }

    @Test fun `without that kind, any newest live model, previews only as a last resort`() {
        assertEquals("models/gemini-4.0-flash-live", bestModel(listOf("models/gemini-4.0-flash-live", "models/gemini-4.5-live-exp", "models/gemini-5.0-pro"), live))
        assertEquals("models/gemini-4.5-live-exp", bestModel(listOf("models/gemini-4.5-live-exp"), live))
    }

    @Test fun `the note writer moves to the newest flash-lite`() {
        assertEquals("models/gemini-4.0-flash-lite", bestModel(listOf("models/gemini-3.5-flash-lite", "models/gemini-4.0-flash-lite", "models/gemini-4.0-pro"), "gemini-3.5-flash-lite"))
    }

    @Test fun `knows Gemini's retired-model answers`() {
        assertTrue(Models.gone("models/gemini-3.8-live-extended-thinking is not found for API version v1beta, or is not supported for bidiGenerateContent"))
        assertEquals(false, Models.gone("Quota exceeded for metric"))
    }
}
