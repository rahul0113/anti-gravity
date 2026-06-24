package com.antigravity.vibecoder.data

import android.content.Context
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
            it + ChatMessage(sender = "user", text = prompt, type = MessageType.USER)
        }
        _isProcessing.value = true

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val responseContent = StringBuilder()
            try {
                _messages.update {
                    it + ChatMessage(sender = "agent", text = "", type = MessageType.AGENT_RESPONSE)
                }

                when (config.executionMode) {
                    ExecutionMode.OPENCLAUDE -> {
                        executeWithOpenClaude(prompt, config, modelName, responseContent)
                    }
                    ExecutionMode.SANDBOX -> {
                        // Termux-based API call
                        val cmd = buildSandboxCommand(prompt, apiKey, baseUrl, modelName)
                        val result = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                        if (result.error == null) responseContent.append(result.stdout)
                        else responseContent.append("Error: ${result.error}")
                    }
                    ExecutionMode.TERMUX_SERVICE -> {
                        val result = TermuxRunner.executeCommand(
                            context, "echo '${prompt.sq()}' | claude --print", config.workspacePath
                        )
                        if (result.error == null) responseContent.append(result.stdout)
                        else responseContent.append("Error: ${result.error ?: result.stderr}")
                    }
                    ExecutionMode.SSH -> {
                        responseContent.append("SSH mode: use Termux to connect, then run OpenClaude from there.")
                    }
                }

                if (responseContent.isNotEmpty()) {
                    updateLastAgentMessage(responseContent.toString())
                    saveCurrentChat(prompt)
                }
            } catch (e: CancellationException) {
                if (responseContent.isNotEmpty()) updateLastAgentMessage(responseContent.toString())
                else _messages.update { msgs ->
                    val last = msgs.indexOfLast { it.type == MessageType.AGENT_RESPONSE }
                    if (last >= 0) msgs.toMutableList().apply { removeAt(last) } else msgs
                }
            } catch (e: Exception) {
                _messages.update { msgs ->
                    val cleaned = msgs.dropLastWhile { it.type == MessageType.AGENT_RESPONSE && it.text.isEmpty() }
                    cleaned + ChatMessage(sender = "system", text = "Error: ${e.message}", type = MessageType.SYSTEM_ERROR)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun updateLastAgentMessage(newText: String) {
        _messages.update { msgs ->
            val idx = msgs.indexOfLast { it.type == MessageType.AGENT_RESPONSE }
            if (idx >= 0) msgs.toMutableList().apply { set(idx, this[idx].copy(text = newText)) } else msgs
        }
    }

    private suspend fun executeWithOpenClaude(
        prompt: String, config: ConnectionConfig, modelName: String, responseContent: StringBuilder
    ) {
        if (grpcClient == null || !grpcClient!!.isConnected()) {
            grpcClient = OpenClaudeGrpcClient(host = "127.0.0.1", port = config.grpcPort)
            grpcClient!!.connect()
        }

        grpcClient!!.chat(
            message = prompt,
            workingDirectory = config.workspacePath.ifBlank { "/data/data/com.termux/files/home" },
            model = modelName.takeIf { it.isNotBlank() }
        ).collect { event ->
            when (event) {
                is ChatEvent.TextChunk -> {
                    responseContent.append(event.text)
                    updateLastAgentMessage(responseContent.toString())
                }
                is ChatEvent.ToolStarted -> {
                    _messages.update {
                        it + ChatMessage(sender = "tool", text = "Using ${event.toolName}...", type = MessageType.TOOL_CALL)
                    }
                }
                is ChatEvent.ToolResult -> {
                    if (event.isError) {
                        _messages.update {
                            it + ChatMessage(sender = "tool", text = "${event.toolName}: ${event.output.take(500)}", type = MessageType.TOOL_OUTPUT)
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
                is ChatEvent.Done -> {}
            }
        }
    }

    private fun buildSandboxCommand(prompt: String, apiKey: String, baseUrl: String, model: String): String {
        val json = """{"model":"$model","messages":[{"role":"user","content":"${prompt.replace("\"", "\\\"")}"}]}"""
        return "curl -s -X POST '$baseUrl/chat/completions' -H 'Authorization: Bearer $apiKey' -H 'Content-Type: application/json' -d '$json' | head -c 4000"
    }

    private fun saveCurrentChat(firstPrompt: String) {
        val title = firstPrompt.take(40) + if (firstPrompt.length > 40) "..." else ""
        val session = ChatSession(id = UUID.randomUUID().toString(), title = title, messages = _messages.value.toList(), timestamp = System.currentTimeMillis())
        _sessions.update { (listOf(session) + it).take(20) }
    }

    fun loadSession(session: ChatSession) { _messages.value = session.messages }
    fun clearHistory() { _messages.value = emptyList() }
    fun injectCrashLog(log: String) {
        _messages.value = listOf(ChatMessage(sender = "system", text = "Previous session crashed:\n$log", type = MessageType.SYSTEM_ERROR))
    }

    private fun String.sq(): String = this.replace("'", "'\\''")
}
