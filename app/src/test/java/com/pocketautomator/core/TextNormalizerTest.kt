package com.pocketautomator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalize_stripsZeroWidthSpace() {
        val raw = "\u200BR"
        assertEquals("R", TextNormalizer.normalize(raw))
    }

    @Test
    fun normalize_trimsWhitespace() {
        assertEquals("Ri", TextNormalizer.normalize("  Ri  "))
    }

    @Test
    fun normalize_returnsNullForBlankAfterStrip() {
        assertNull(TextNormalizer.normalize("\u200B"))
        assertNull(TextNormalizer.normalize(null))
    }

    @Test
    fun codePoints_formatsUnicodeScalars() {
        assertEquals("U+0052 U+0069", TextNormalizer.codePoints("Ri"))
        assertEquals("U+200B U+0052", TextNormalizer.codePoints("\u200BR"))
    }
}
