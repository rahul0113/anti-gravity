package com.antigravity.vibecoder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ZenMessage(val role: String, val content: String)

@Serializable
data class ZenRequest(
    val model: String,
    val messages: List<ZenMessage>,
    val temperature: Double = 0.2
)

@Serializable
data class ZenResponse(
    val choices: List<ZenChoice>
)

@Serializable
data class ZenChoice(
    val message: ZenMessage
)

@Serializable
data class ZenModel(val id: String)

@Serializable
data class ZenModelsResponse(val data: List<ZenModel>)

class ZenApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://opencode.ai/zen/v1",
    private val model: String = "opencode/zen-coder-1"
) {
    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val json = Json { ignoreUnknownKeys = true }

        suspend fun getAvailableModels(apiKey: String, baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Failed to fetch models: Code ${response.code}"))
                    }
                    val responseBodyStr = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response body"))
                    val parsed = json.decodeFromString<ZenModelsResponse>(responseBodyStr)
                    Result.success(parsed.data.map { it.id })
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCompletion(messages: List<ZenMessage>): Result<String> = withContext(Dispatchers.IO) {
        val requestBody = ZenRequest(
            model = model,
            messages = messages,
            temperature = 0.2
        )
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val bodyStr = json.encodeToString(requestBody)
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(bodyStr.toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("API Error: Code ${response.code}. Response: $errorBody"))
                }
                
                val responseBodyStr = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response body"))
                val parsed = json.decodeFromString<ZenResponse>(responseBodyStr)
                val text = parsed.choices.firstOrNull()?.message?.content
                    ?: return@withContext Result.failure(IOException("No completion options returned"))
                
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
