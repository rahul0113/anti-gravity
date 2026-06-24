package com.antigravity.vibecoder.data

import android.content.Context
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val timestamp: Long
)

class AgentExecutor(private val context: Context) {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private var currentChatJob: Job? = null
    private var grpcClient: OpenClaudeGrpcClient? = null

    fun executeUserPrompt(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        config: ConnectionConfig
    ) {
        if (prompt.isBlank() || _isProcessing.value) return

        _messages.update {
            it + ChatMessage(
                sender = "user",
                text = prompt,
                type = MessageType.USER,
                timestamp = System.currentTimeMillis()
            )
        }
        _isProcessing.value = true

        currentChatJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val responseContent = StringBuilder()
            try {
                // Add empty agent message as placeholder
                _messages.update {
                    it + ChatMessage(
                        sender = "agent",
                        text = "",
                        type = MessageType.AGENT_RESPONSE,
                        timestamp = System.currentTimeMillis()
                    )
                }

                when (config.executionMode) {
                    ExecutionMode.OPENCLAUDE -> {
                        executeWithOpenClaude(prompt, config, modelName, responseContent)
                    }
                    ExecutionMode.SANDBOX -> {
                        val result = executeInSandbox(prompt, apiKey, baseUrl, modelName)
                        result.fold(
                            onSuccess = { responseContent.append(it) },
                            onFailure = { responseContent.append("Error: ${it.message}") }
                        )
                    }
                    ExecutionMode.TERMUX_SERVICE -> {
                        val result = executeWithTermux(prompt, config)
                        result.fold(
                            onSuccess = { responseContent.append(it) },
                            onFailure = { responseContent.append("Error: ${it.message}") }
                        )
                    }
                    ExecutionMode.SSH -> {
                        val result = executeWithSsh(prompt, config)
                        result.fold(
                            onSuccess = { responseContent.append(it) },
                            onFailure = { responseContent.append("Error: ${it.message}") }
                        )
                    }
                }

                if (responseContent.isNotEmpty()) {
                    val finalContent = responseContent.toString()
                    updateLastAgentMessage(finalContent)
                    saveCurrentChat(prompt)
                }
            } catch (e: CancellationException) {
                val finalContent = responseContent.toString()
                if (finalContent.isNotEmpty()) {
                    updateLastAgentMessage(finalContent)
                } else {
                    // Remove empty agent placeholder
                    _messages.update { messages ->
                        val lastAgent = messages.indexOfLast { it.type == MessageType.AGENT_RESPONSE }
                        if (lastAgent >= 0) messages.toMutableList().apply { removeAt(lastAgent) } else messages
                    }
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    sender = "system",
                    text = "Error: ${e.message ?: "Unknown error occurred"}",
                    type = MessageType.SYSTEM_ERROR,
                    timestamp = System.currentTimeMillis()
                )
                _messages.update { messages ->
                    val cleaned = messages.dropLastWhile {
                        it.type == MessageType.AGENT_RESPONSE && it.text.isEmpty()
                    }
                    cleaned + errorMsg
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Update the last AGENT_RESPONSE message's text */
    private fun updateLastAgentMessage(newText: String) {
        _messages.update { messages ->
            val lastIdx = messages.indexOfLast { it.type == MessageType.AGENT_RESPONSE }
            if (lastIdx >= 0) {
                messages.toMutableList().apply {
                    set(lastIdx, this[lastIdx].copy(text = newText))
                }
            } else messages
        }
    }

    private suspend fun executeWithOpenClaude(
        prompt: String,
        config: ConnectionConfig,
        modelName: String,
        responseContent: StringBuilder
    ) {
        if (grpcClient == null || !grpcClient!!.isConnected()) {
            grpcClient = OpenClaudeGrpcClient(
                host = "127.0.0.1",
                port = config.grpcPort
            )
            grpcClient!!.connect()
        }

        val workDir = config.workspacePath.ifBlank {
            "/data/data/com.termux/files/home"
        }

        grpcClient!!.chat(
            message = prompt,
            workingDirectory = workDir,
            model = modelName.takeIf { it.isNotBlank() }
        ).collect { event ->
            when (event) {
                is ChatEvent.TextChunk -> {
                    responseContent.append(event.text)
                    updateLastAgentMessage(responseContent.toString())
                }
                is ChatEvent.ToolStarted -> {
                    _messages.update {
                        it + ChatMessage(
                            sender = "tool",
                            text = "\n\nUsing ${event.toolName}...",
                            type = MessageType.TOOL_CALL,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
                is ChatEvent.ToolResult -> {
                    if (event.isError) {
                        _messages.update {
                            it + ChatMessage(
                                sender = "tool",
                                text = "${event.toolName} error: ${event.output.take(500)}",
                                type = MessageType.TOOL_OUTPUT,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
                is ChatEvent.ActionRequired -> {
                    responseContent.append("\n\n${event.question}")
                    updateLastAgentMessage(responseContent.toString())
                }
                is ChatEvent.Error -> {
                    responseContent.append("\n\nError: ${event.message}")
                    updateLastAgentMessage(responseContent.toString())
                }
                is ChatEvent.Done -> { /* Stream completed */ }
            }
        }
    }

    private suspend fun executeInSandbox(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        modelName: String
    ): Result<String> {
        val client = ZenApiClient(apiKey = apiKey, baseUrl = baseUrl, model = modelName)
        return client.sendMessage(listOf(ZenMessage("user", prompt)))
    }

    private suspend fun executeWithTermux(prompt: String, config: ConnectionConfig): Result<String> {
        val result = TermuxRunner.executeCommand(context, "echo '${prompt.sq()}' | claude --print", config.workspacePath)
        return if (result.error == null && result.stderr.isBlank()) {
            Result.success(result.stdout)
        } else {
            Result.failure(Exception(result.error ?: result.stderr.ifBlank { "Command failed with exit code ${result.exitCode}" }))
        }
    }

    private suspend fun executeWithSsh(prompt: String, config: ConnectionConfig): Result<String> {
        return try {
            val sshResult = SshConnection.executeCommand(config, "echo '${prompt.sq()}' | claude --print")
            Result.success(sshResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveCurrentChat(firstPrompt: String) {
        val title = if (firstPrompt.length > 40) firstPrompt.take(40) + "..." else firstPrompt
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            messages = _messages.value.toList(),
            timestamp = System.currentTimeMillis()
        )
        _sessions.update { (listOf(session) + it).take(20) }
    }

    fun loadSession(session: ChatSession) {
        _messages.value = session.messages
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    fun injectCrashLog(log: String) {
        _messages.value = listOf(
            ChatMessage(
                sender = "system",
                text = "Previous session crashed:\n$log",
                type = MessageType.SYSTEM_ERROR,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun String.sq(): String = this.replace("'", "'\\''")
}
