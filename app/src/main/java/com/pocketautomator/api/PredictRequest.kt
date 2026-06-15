package com.pocketautomator.api

import kotlinx.serialization.Serializable

@Serializable
data class PredictRequest(
    val prompt: String
)
