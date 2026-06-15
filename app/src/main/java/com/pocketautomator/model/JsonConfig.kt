package com.pocketautomator.model

import kotlinx.serialization.json.Json

object JsonConfig {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
