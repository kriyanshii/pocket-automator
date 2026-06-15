package com.pocketautomator.core

object PlaceholderText {

    private val EXACT_MATCHES = setOf(
        "Message",
        "Search",
        "Type a message",
        "Search name or number",
        "What do you want to listen to?",
        "What do you want to play?"
    )

    fun isPlaceholderValue(value: String?): Boolean {
        val normalized = TextNormalizer.normalize(value) ?: return true
        if (EXACT_MATCHES.any { it.equals(normalized, ignoreCase = true) }) return true
        if (normalized.startsWith("Search ", ignoreCase = true) && normalized.length > 20) return true
        if (normalized.startsWith("What do you want to", ignoreCase = true)) return true
        return false
    }
}
