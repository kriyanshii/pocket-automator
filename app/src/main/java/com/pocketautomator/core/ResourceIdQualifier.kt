package com.pocketautomator.core

import com.pocketautomator.model.Selector

object ResourceIdQualifier {

    fun qualify(resourceId: String, packageName: String?): String {
        if (resourceId.contains(':')) return resourceId
        if (packageName.isNullOrBlank()) return resourceId
        return "$packageName:id/$resourceId"
    }

    fun normalizeSelector(selector: Selector, packageName: String?): Selector {
        val resourceId = selector.resourceId ?: return selector
        val qualified = qualify(resourceId, packageName)
        return if (qualified == resourceId) selector else selector.copy(resourceId = qualified)
    }
}
