package com.pocketautomator.core

import com.pocketautomator.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeCaptureTest {

    @Test
    fun screenNode_usesNestedChildrenStructure() {
        val root = ScreenNode(
            nodeId = "1",
            text = "Root",
            children = listOf(
                ScreenNode(nodeId = "2", text = "Child", clickable = true)
            )
        )
        assertEquals("1", root.nodeId)
        assertEquals(1, root.children.size)
        assertEquals("Child", root.children[0].text)
        assertTrue(root.children[0].clickable)
    }
}
