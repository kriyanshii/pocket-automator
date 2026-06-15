package com.pocketautomator.core

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.ActionType
import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.Selector

object MessageSender {

    private val WHATSAPP_SEND_RESOURCE_IDS = listOf(
        "com.whatsapp:id/send",
        "com.whatsapp:id/send_button",
        "com.whatsapp:id/conversation_entry_send"
    )

    fun isMessageEntryStep(step: ExportStep): Boolean {
        if (!step.packageName.contains("whatsapp", ignoreCase = true)) return false
        if (step.action.type != "set_text") return false
        return step.action.resourceId?.substringAfterLast('/') == "entry"
    }

    fun isSendAction(action: ExportAction): Boolean {
        if (action.type != "click" && action.type != "long_click") return false
        val id = action.resourceId?.lowercase().orEmpty()
        if (id.contains("send")) return true
        return action.contentDescription.equals("Send", ignoreCase = true)
    }

    fun shouldAutoSendAfterMessage(step: ExportStep, nextStep: ExportStep?): Boolean {
        if (!isMessageEntryStep(step)) return false
        if (nextStep == null) return true
        return !isSendAction(nextStep.action)
    }

    fun trySend(
        root: AccessibilityNodeInfo,
        nodeFinder: NodeFinder,
        performClick: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        for (resourceId in WHATSAPP_SEND_RESOURCE_IDS) {
            val node = nodeFinder.find(root, Selector(resourceId = resourceId), ActionType.CLICK)
            if (node != null && performClick(node)) {
                node.recycle()
                return true
            }
            node?.recycle()
        }

        val byDesc = nodeFinder.find(
            root,
            Selector(contentDescription = "Send"),
            ActionType.CLICK
        )
        if (byDesc != null && performClick(byDesc)) {
            byDesc.recycle()
            return true
        }
        byDesc?.recycle()
        return false
    }

    fun tryImeSend(entryNode: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return entryNode.performAction(ACTION_IME_ENTER)
    }

    private const val ACTION_IME_ENTER = 0x01000008
}
