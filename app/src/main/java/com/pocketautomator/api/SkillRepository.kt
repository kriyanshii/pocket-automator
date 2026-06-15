package com.pocketautomator.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pocketautomator.BuildConfig
import com.pocketautomator.model.JsonConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException

class SkillRepository(
    private val apiService: SkillApiService
) {
    suspend fun predictSkill(prompt: String): String {
        return try {
            apiService.predict(PredictRequest(prompt)).skill
        } catch (e: HttpException) {
            throw IOException(parseHttpError(e), e)
        }
    }

    private fun parseHttpError(e: HttpException): String {
        val body = e.response()?.errorBody()?.string().orEmpty()
        if (body.isBlank()) {
            return "Prediction failed (HTTP ${e.code()})"
        }
        return runCatching {
            val json = JsonConfig.json.parseToJsonElement(body)
            when {
                json is JsonObject && json["message"] != null ->
                    json["message"]!!.jsonPrimitive.content
                json is JsonObject && json["error"] != null ->
                    json["error"]!!.jsonPrimitive.content
                json is JsonObject && json["detail"] is JsonArray -> {
                    val detail = json["detail"] as JsonArray
                    val first = detail.firstOrNull() as? JsonObject
                    first?.get("msg")?.jsonPrimitive?.content
                        ?: "Invalid request (HTTP ${e.code()})"
                }
                else -> "Prediction failed (HTTP ${e.code()})"
            }
        }.getOrDefault("Prediction failed (HTTP ${e.code()})")
    }

    companion object {
        fun create(): SkillRepository {
            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .addConverterFactory(JsonConfig.json.asConverterFactory(contentType))
                .build()
            return SkillRepository(retrofit.create(SkillApiService::class.java))
        }
    }
}
