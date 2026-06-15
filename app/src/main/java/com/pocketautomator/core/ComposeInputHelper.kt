package com.pocketautomator.core

import com.pocketautomator.model.Selector

object ComposeInputHelper {

    private val BODY_SUFFIXES = setOf("editor", "body", "message", "content", "compose_text")
    private val TITLE_SUFFIXES = setOf("title", "subject")

    fun isComposeBodySelector(selector: Selector): Boolean {
        val suffix = selector.resourceId?.substringAfterLast('/')?.lowercase().orEmpty()
        return suffix in BODY_SUFFIXES
    }

    fun isTitleOrSubjectSelector(selector: Selector): Boolean {
        val suffix = selector.resourceId?.substringAfterLast('/')?.lowercase().orEmpty()
        return suffix in TITLE_SUFFIXES
    }

    fun placeholderHintsFor(selector: Selector): List<String> {
        val suffix = selector.resourceId?.substringAfterLast('/')?.lowercase().orEmpty()
        return when (suffix) {
            "title" -> listOf("Add title", "Event title", "Title")
            "subject" -> listOf("Subject", "Add subject")
            "editor", "body" -> listOf("Compose email", "Body")
            else -> emptyList()
        }
    }
}
