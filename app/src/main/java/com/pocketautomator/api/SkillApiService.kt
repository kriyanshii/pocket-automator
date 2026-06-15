package com.pocketautomator.api

import retrofit2.http.Body
import retrofit2.http.POST

interface SkillApiService {
    @POST("predict")
    suspend fun predict(@Body request: PredictRequest): PredictResponse
}
