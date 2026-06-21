package com.antigravity.vibecoder.data

import android.content.Context
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.io.File

class AgentExecutor(private val context: Context) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    // B-4 FIX: Cache ZenApiClient so OkHttpClient connection pool is reused across messages
    private var zenApiClient: ZenApiClient? = null
    private var cachedApiKey = ""
    private var cachedBaseUrl = ""
    private var cachedModel = ""

    private fun getZenClient(apiKey: String, baseUrl: String, model: String): ZenApiClient {
        if (zenApiClient == null || apiKey != cachedApiKey || baseUrl != cachedBaseUrl || model != cachedModel) {
            zenApiClient = ZenApiClient(apiKey, baseUrl, model)
            cachedApiKey = apiKey
            cachedBaseUrl = baseUrl
            cachedModel = model
        }
        return zenApiClient!!
    }

    // C-5 FIX: StateFlow.update{} is atomic — prevents lost updates from concurrent addMessage calls
    private fun addMessage(text: String, type: MessageType, sender: String = "System") {
        _messages.update { current -> current + ChatMessage(sender = sender, text = text, type = type) }
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    fun injectCrashLog(log: String) {
        _messages.update { current ->
            current + ChatMessage(
                sender = "CrashReporter",
                text = "⚠️ PREVIOUS SESSION CRASHED — Stack trace below:\n\n$log",
                type = MessageType.SYSTEM_ERROR
            )
        }
    }

    private fun String.shellSingleQuote(): String = "'" + this.replace("'", "'\\''") + "'"

    suspend fun executeUserPrompt(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        config: ConnectionConfig
    ) {
        if (!_isProcessing.compareAndSet(expect = false, update = true)) return
        addMessage(prompt, MessageType.USER, "User")

        try {
            // S-3 FIX: Top-level 120-second timeout guards all execution modes including SSH hangs
            withTimeout(120_000L) {
                when (config.executionMode) {
                    ExecutionMode.TERMUX_SERVICE -> {
                        // B-5 FIX: Guard missing API key for non-SANDBOX modes explicitly
                        if (apiKey.isEmpty()) {
                            addMessage("No API key set. Please go to SETTINGS first.", MessageType.SYSTEM_ERROR)
                            return@withTimeout
                        }
                        addMessage("Executing opencode CLI via Termux IPC...", MessageType.AGENT_THOUGHT, "System")

                        // O-7 & P-2 FIX: Single combined command — passed directly via exports without temp file
                        // Secure: variables are inline, no risk of leaving a file in /data/local/tmp
                        val combinedCmd = buildString {
                            append("export OPENCODE_API_KEY=${apiKey.shellSingleQuote()} && ")
                            append("export OPENCODE_BASE_URL=${baseUrl.shellSingleQuote()} && ")
                            append("export OPENCODE_MODEL=${model.shellSingleQuote()} && ")
                            append("cd ${config.workspacePath.shellSingleQuote()} && ")
                            append("opencode run ${prompt.shellSingleQuote()}")
                        }
                        val res = TermuxRunner.executeCommand(context, combinedCmd, config.workspacePath)
                        handleTermuxResult(res)
                    }

                    ExecutionMode.SSH -> {
                        if (apiKey.isEmpty()) {
                            addMessage("No API key set. Please go to SETTINGS first.", MessageType.SYSTEM_ERROR)
                            return@withTimeout
                        }
                        addMessage("Executing opencode CLI via SSH...", MessageType.AGENT_THOUGHT, "System")
                        // B-2 FIX: Include OPENCODE_BASE_URL and OPENCODE_MODEL for SSH mode
                        val sshCommand = "cd ${config.workspacePath.shellSingleQuote()} && " +
                            "OPENCODE_API_KEY=${apiKey.shellSingleQuote()} " +
                            "OPENCODE_BASE_URL=${baseUrl.shellSingleQuote()} " +
                            "OPENCODE_MODEL=${model.shellSingleQuote()} " +
                            "opencode run ${prompt.shellSingleQuote()}"
                        val output = SshConnection.executeCommand(config, sshCommand)
                        addMessage(output, MessageType.TOOL_OUTPUT, "opencode")
                    }

                    ExecutionMode.SANDBOX -> {
                        if (apiKey.isEmpty()) {
                            addMessage(
                                "No API key configured. Go to SETTINGS → Zen API Key.",
                                MessageType.SYSTEM_ERROR
                            )
                            return@withTimeout
                        }
                        addMessage("Calling OpenCode Zen API (Sandbox mode)...", MessageType.AGENT_THOUGHT, "System")
                        val history = _messages.value
                            .filter { it.type == MessageType.USER || it.type == MessageType.AGENT_RESPONSE }
                            .map { ZenMessage(if (it.type == MessageType.USER) "user" else "assistant", it.text) }

                        // B-4 FIX: Use cached client instead of creating a new one each call
                        val client = getZenClient(apiKey, baseUrl, model)
                        val result = client.getCompletion(history)
                        result.fold(
                            onSuccess = { addMessage(it, MessageType.AGENT_RESPONSE, "Zen AI") },
                            onFailure = { addMessage("API Error: ${it.message}", MessageType.SYSTEM_ERROR) }
                        )
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            addMessage("Operation timed out after 120 seconds. Check your connection and try again.", MessageType.SYSTEM_ERROR)
        } catch (e: Exception) {
            addMessage("Unexpected error: ${e.message}", MessageType.SYSTEM_ERROR)
        } finally {
            _isProcessing.value = false
        }
    }

    private fun handleTermuxResult(res: TermuxRunner.TermuxResult) {
        if (res.error != null) {
            addMessage(
                "Termux Error: ${res.error}\nIs opencode installed? Run: pkg install opencode",
                MessageType.SYSTEM_ERROR
            )
        } else {
            if (res.stdout.isNotEmpty()) addMessage(res.stdout, MessageType.TOOL_OUTPUT, "opencode")
            if (res.stderr.isNotEmpty()) addMessage(res.stderr, MessageType.SYSTEM_ERROR)
        }
    }
}
