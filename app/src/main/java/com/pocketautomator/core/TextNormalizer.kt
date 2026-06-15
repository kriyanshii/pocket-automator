package com.pocketautomator.core

object TextNormalizer {

    private val INVISIBLE_CHARS = Regex("[\u200B\u200C\u200D\uFEFF]")

    fun normalize(value: String?): String? {
        if (value == null) return null
        val normalized = value.replace(INVISIBLE_CHARS, "").trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    fun codePoints(value: String): String {
        return value.map { ch ->
            "U+${ch.code.toString(16).uppercase().padStart(4, '0')}"
        }.joinToString(" ")
    }
}
