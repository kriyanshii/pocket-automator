package com.pocketautomator.api

import kotlinx.serialization.Serializable

@Serializable
data class PredictResponse(
    val skill: String
)
