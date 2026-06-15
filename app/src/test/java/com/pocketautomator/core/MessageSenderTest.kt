package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSenderTest {

    @Test
    fun shouldAutoSendAfterMessage_whenNoSendStepFollows() {
        val typeStep = ExportStep(
            timestamp = 0,
            screen = ScreenNode(nodeId = "1"),
            action = ExportAction(
                type = "set_text",
                resourceId = "com.whatsapp:id/entry",
                value = "Hi"
            ),
            packageName = "com.whatsapp"
        )
        assertTrue(MessageSender.shouldAutoSendAfterMessage(typeStep, nextStep = null))
    }

    @Test
    fun shouldAutoSendAfterMessage_falseWhenSendClickFollows() {
        val typeStep = ExportStep(
            timestamp = 0,
            screen = ScreenNode(nodeId = "1"),
            action = ExportAction(type = "set_text", resourceId = "com.whatsapp:id/entry", value = "Hi"),
            packageName = "com.whatsapp"
        )
        val sendStep = ExportStep(
            timestamp = 1,
            screen = ScreenNode(nodeId = "2"),
            action = ExportAction(type = "click", resourceId = "com.whatsapp:id/send"),
            packageName = "com.whatsapp"
        )
        assertFalse(MessageSender.shouldAutoSendAfterMessage(typeStep, sendStep))
    }

    @Test
    fun isSendAction_detectsWhatsAppSendButton() {
        assertTrue(
            MessageSender.isSendAction(
                ExportAction(type = "click", resourceId = "com.whatsapp:id/send")
            )
        )
    }
}
