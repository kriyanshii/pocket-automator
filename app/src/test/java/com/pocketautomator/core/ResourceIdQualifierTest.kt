package com.pocketautomator.core

import com.pocketautomator.model.Selector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceIdQualifierTest {

    @Test
    fun qualify_addsPackagePrefixToBareId() {
        assertEquals(
            "com.google.android.gm:id/editor",
            ResourceIdQualifier.qualify("editor", "com.google.android.gm")
        )
    }

    @Test
    fun qualify_keepsFullyQualifiedId() {
        assertEquals(
            "com.google.android.calendar:id/title",
            ResourceIdQualifier.qualify("com.google.android.calendar:id/title", "com.google.android.calendar")
        )
    }

    @Test
    fun qualify_returnsBareIdWhenPackageMissing() {
        assertEquals("editor", ResourceIdQualifier.qualify("editor", null))
    }

    @Test
    fun normalizeSelector_updatesBareResourceId() {
        val selector = Selector(resourceId = "editor", className = "android.widget.EditText")
        val normalized = ResourceIdQualifier.normalizeSelector(selector, "com.google.android.gm")
        assertEquals("com.google.android.gm:id/editor", normalized.resourceId)
        assertEquals("android.widget.EditText", normalized.className)
    }

    @Test
    fun normalizeSelector_leavesNullResourceId() {
        val selector = Selector(text = "Hello")
        val normalized = ResourceIdQualifier.normalizeSelector(selector, "com.google.android.gm")
        assertNull(normalized.resourceId)
        assertEquals("Hello", normalized.text)
    }
}
