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
    val temperature: Double = 0.2,
    val stream: Boolean = false
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

enum class Provider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    GITHUB_MODELS("GitHub Models", "https://models.inference.ai.azure.com", "gpt-4o"),
    OLLAMA("Ollama (Local)", "http://localhost:11434/v1", "llama3.2"),
    OPENCLAUDE("OpenClaude (gRPC)", "", "")
}

class ZenApiClient(
    private val apiKey: String,
    private val baseUrl: String = Provider.OPENAI.defaultBaseUrl,
    private val model: String = Provider.OPENAI.defaultModel
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
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw IOException("Empty response")
                if (response.isSuccessful) {
                    val modelsResponse = json.decodeFromString<ZenModelsResponse>(body)
                    Result.success(modelsResponse.data.map { it.id })
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(
        messages: List<ZenMessage>,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val requestBody = ZenRequest(
            model = modelOverride ?: model,
            messages = messages
        )

        val jsonBody = json.encodeToString(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://anti-gravity.app")
            .addHeader("X-Title", "Anti-Gravity Vibe Coder")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")

            if (response.isSuccessful) {
                val chatResponse = json.decodeFromString<ZenResponse>(responseBody)
                val content = chatResponse.choices.firstOrNull()?.message?.content
                    ?: throw IOException("No response content")
                Result.success(content)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
