package com.pocketautomator.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    SCROLL
}

@Serializable
data class Selector(
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val path: List<Int>? = null,
    val className: String? = null
)

@Serializable
data class Action(
    val type: ActionType,
    val selector: Selector? = null,
    val value: String? = null
)

@Serializable
data class ScreenNode(
    val nodeId: String,
    val text: String? = null,
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val className: String? = null,
    val bounds: String = "",
    val children: List<ScreenNode> = emptyList()
)

@Serializable
data class ExportAction(
    val type: String,
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val path: List<Int>? = null,
    val className: String? = null,
    val value: String? = null,
    val direction: String? = null
) {
    companion object {
        fun from(action: Action): ExportAction {
            val selector = action.selector
            val actionType = when (action.type) {
                ActionType.CLICK -> "click"
                ActionType.LONG_CLICK -> "long_click"
                ActionType.SET_TEXT -> "set_text"
                ActionType.SCROLL -> "scroll"
            }
            return ExportAction(
                type = actionType,
                resourceId = selector?.resourceId,
                contentDescription = selector?.contentDescription,
                text = selector?.text,
                path = selector?.path,
                className = selector?.className,
                value = if (action.type == ActionType.SET_TEXT) action.value else null,
                direction = if (action.type == ActionType.SCROLL) action.value else null
            )
        }
    }

    fun toAction(): Action {
        val selector = Selector(
            resourceId = resourceId,
            contentDescription = contentDescription,
            text = text,
            path = path,
            className = className
        )
        val hasSelector = resourceId != null || contentDescription != null ||
            text != null || !path.isNullOrEmpty() || className != null
        return Action(
            type = when (type) {
                "click" -> ActionType.CLICK
                "long_click" -> ActionType.LONG_CLICK
                "set_text" -> ActionType.SET_TEXT
                "scroll" -> ActionType.SCROLL
                else -> ActionType.CLICK
            },
            selector = if (hasSelector) selector else null,
            value = when (type) {
                "set_text" -> value
                "scroll" -> direction
                else -> null
            }
        )
    }
}

@Serializable
data class ExportStep(
    val timestamp: Long,
    val screen: ScreenNode,
    val action: ExportAction,
    val packageName: String = ""
)

@Serializable
data class ExportRecording(
    val task: String,
    val app: String,
    val steps: List<ExportStep> = emptyList(),
    val id: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class RecordingMeta(
    val id: String,
    val task: String,
    val app: String,
    val stepCount: Int,
    val createdAt: Long
)

data class RecordingStep(
    val timestamp: Long,
    val packageName: String,
    val screen: ScreenNode,
    val action: ExportAction
)
