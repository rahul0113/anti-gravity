package com.antigravity.vibecoder.data

import io.grpc.*
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import openclaude.v1.AgentServiceGrpcKt
import openclaude.v1.OpenClaudeProto
import java.util.concurrent.TimeUnit

class OpenClaudeGrpcClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 50051
) {
    private var channel: ManagedChannel? = null
    private var stub: AgentServiceGrpcKt.AgentServiceCoroutineStub? = null

    fun connect() {
        channel = OkHttpChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .build()
        stub = AgentServiceGrpcKt.AgentServiceCoroutineStub(channel!!)
    }

    fun disconnect() {
        channel?.shutdown()
        try {
            channel?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}
        channel = null
        stub = null
    }

    fun isConnected(): Boolean = channel != null && !channel?.isShutdown!!

    fun chat(
        message: String,
        workingDirectory: String = "/data/data/com.termux/files/home",
        model: String? = null,
        sessionId: String = ""
    ): Flow<ChatEvent> = callbackFlow {
        val currentStub = stub ?: throw IllegalStateException("Not connected to OpenClaude")

        val requestFlow = flow {
            // Send initial chat request
            emit(OpenClaudeProto.ClientMessage.newBuilder().apply {
                requestBuilder = OpenClaudeProto.ChatRequest.newBuilder()
                    .setMessage(message)
                    .setWorkingDirectory(workingDirectory)
                    .apply {
                        if (!model.isNullOrBlank()) setModel(model)
                        if (sessionId.isNotBlank()) setSessionId(sessionId)
                    }
                    .build()
            }.build())
        }

        try {
            val responseStream = currentStub.chat(requestFlow)
            responseStream.collect { serverMessage ->
                when (serverMessage.eventCase) {
                    OpenClaudeProto.ServerMessage.EventCase.TEXT_CHUNK -> {
                        send(ChatEvent.TextChunk(serverMessage.textChunk.text))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.TOOL_START -> {
                        send(ChatEvent.ToolStarted(
                            toolName = serverMessage.toolStart.toolName,
                            argumentsJson = serverMessage.toolStart.argumentsJson,
                            toolUseId = serverMessage.toolStart.toolUseId
                        ))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.TOOL_RESULT -> {
                        send(ChatEvent.ToolResult(
                            toolName = serverMessage.toolResult.toolName,
                            output = serverMessage.toolResult.output,
                            isError = serverMessage.toolResult.isError,
                            toolUseId = serverMessage.toolResult.toolUseId
                        ))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.ACTION_REQUIRED -> {
                        send(ChatEvent.ActionRequired(
                            promptId = serverMessage.actionRequired.promptId,
                            question = serverMessage.actionRequired.question,
                            type = when (serverMessage.actionRequired.type) {
                                OpenClaudeProto.ActionRequired.ActionType.CONFIRM_COMMAND -> ActionType.CONFIRM_COMMAND
                                OpenClaudeProto.ActionRequired.ActionType.REQUEST_INFORMATION -> ActionType.REQUEST_INFORMATION
                                else -> ActionType.CONFIRM_COMMAND
                            }
                        ))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.DONE -> {
                        send(ChatEvent.Done(
                            fullText = serverMessage.done.fullText,
                            promptTokens = serverMessage.done.promptTokens,
                            completionTokens = serverMessage.done.completionTokens
                        ))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.ERROR -> {
                        send(ChatEvent.Error(
                            message = serverMessage.error.message,
                            code = serverMessage.error.code
                        ))
                    }
                    OpenClaudeProto.ServerMessage.EventCase.EVENT_NOT_SET -> {}
                }
            }
        } catch (e: Exception) {
            send(ChatEvent.Error(e.message ?: "Unknown gRPC error", "GRPC_ERROR"))
        }

        awaitClose()
    }

    suspend fun sendInput(stub: AgentServiceGrpcKt.AgentServiceCoroutineStub, promptId: String, reply: String) {
        // For sending responses to action required prompts, we'd need a persistent stream
        // This is handled at a higher level with session management
    }
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
