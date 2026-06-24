package com.antigravity.vibecoder.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
data class OpenClaudeRequest(val message: String, val working_directory: String, val model: String? = null, val session_id: String? = null)

@Serializable
data class OpenClaudeEvent(val event: String, val data: String)

class OpenClaudeGrpcClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 50051
) {
    private var client: OkHttpClient? = null
    private var connected = false

    fun connect() {
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for streaming
            .build()
        connected = true
    }

    fun disconnect() {
        client?.dispatcher?.executorService?.shutdown()
        client = null
        connected = false
    }

    fun isConnected(): Boolean = connected

    fun chat(
        message: String,
        workingDirectory: String = "/data/data/com.termux/files/home",
        model: String? = null,
        sessionId: String = ""
    ): Flow<ChatEvent> = callbackFlow {
        val currentClient = client ?: throw IllegalStateException("Not connected to OpenClaude")
        val json = Json { ignoreUnknownKeys = true }

        val request = OpenClaudeRequest(
            message = message,
            working_directory = workingDirectory,
            model = model,
            session_id = sessionId.ifBlank { null }
        )

        val jsonBody = json.encodeToString(request)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url("http://$host:$port/chat")
            .post(body)
            .addHeader("Accept", "text/event-stream")
            .build()

        try {
            val response = currentClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                send(ChatEvent.Error("HTTP ${response.code}: ${response.message}", "HTTP_ERROR"))
                return@callbackFlow
            }

            val reader = response.body?.source()?.buffer()?.inputStream()?.bufferedReader()
                ?: throw IOException("No response body")

            reader.use { br ->
                var eventType = ""
                while (true) {
                    val line = br.readLine() ?: break
                    when {
                        line.startsWith("event: ") -> eventType = line.removePrefix("event: ").trim()
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break

                            when (eventType) {
                                "text_chunk" -> send(ChatEvent.TextChunk(data))
                                "tool_start" -> {
                                    try {
                                        val toolEvent = json.decodeFromString<ToolStartEvent>(data)
                                        send(ChatEvent.ToolStarted(toolEvent.tool_name, toolEvent.arguments_json, toolEvent.tool_use_id))
                                    } catch (_: Exception) {
                                        send(ChatEvent.TextChunk("\n\nUsing tool: $data"))
                                    }
                                }
                                "tool_result" -> {
                                    try {
                                        val resultEvent = json.decodeFromString<ToolResultEvent>(data)
                                        send(ChatEvent.ToolResult(resultEvent.tool_name, resultEvent.output, resultEvent.is_error, resultEvent.tool_use_id))
                                    } catch (_: Exception) {}
                                }
                                "action_required" -> {
                                    try {
                                        val actionEvent = json.decodeFromString<ActionEvent>(data)
                                        send(ChatEvent.ActionRequired(actionEvent.prompt_id, actionEvent.question, ActionType.CONFIRM_COMMAND))
                                    } catch (_: Exception) {}
                                }
                                "done" -> {
                                    send(ChatEvent.Done(data, 0, 0))
                                }
                                "error" -> {
                                    send(ChatEvent.Error(data, "AGENT_ERROR"))
                                }
                            }
                        }
                        line.isBlank() -> {
                            // Empty line separates events in SSE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            send(ChatEvent.Error(e.message ?: "Connection error", "CONNECTION_ERROR"))
        }

        awaitClose()
    }

    @Serializable
    private data class ToolStartEvent(val tool_name: String, val arguments_json: String, val tool_use_id: String)
    @Serializable
    private data class ToolResultEvent(val tool_name: String, val output: String, val is_error: Boolean, val tool_use_id: String)
    @Serializable
    private data class ActionEvent(val prompt_id: String, val question: String)
}

sealed class ChatEvent {
    data class TextChunk(val text: String) : ChatEvent()
    data class ToolStarted(val toolName: String, val argumentsJson: String, val toolUseId: String) : ChatEvent()
    data class ToolResult(val toolName: String, val output: String, val isError: Boolean, val toolUseId: String) : ChatEvent()
    data class ActionRequired(val promptId: String, val question: String, val type: ActionType) : ChatEvent()
    data class Done(val fullText: String, val promptTokens: Int, val completionTokens: Int) : ChatEvent()
    data class Error(val message: String, val code: String) : ChatEvent()
}

enum class ActionType {
    CONFIRM_COMMAND,
    REQUEST_INFORMATION
}
