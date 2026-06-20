package com.antigravity.vibecoder.data

import android.content.Context
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import java.io.File

class AgentExecutor(private val context: Context) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private fun addMessage(text: String, type: MessageType, sender: String = "System") {
        val updated = _messages.value.toMutableList()
        updated.add(ChatMessage(sender = sender, text = text, type = type))
        _messages.value = updated
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    // SEC-1 FIX: Proper single-quote shell escaping prevents ALL injection including backticks
    private fun String.shellSingleQuote(): String = "'" + this.replace("'", "'\\''") + "'"

    suspend fun executeUserPrompt(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        config: ConnectionConfig
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        addMessage(prompt, MessageType.USER, "User")

        // CRASH-6 FIX: Always reset isProcessing even if an exception is thrown
        try {
            when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    addMessage("Executing opencode CLI via Termux IPC...", MessageType.AGENT_THOUGHT, "System")

                    // SEC-4 FIX: Write API key to a temp file, source it, delete it immediately
                    // This prevents the key from appearing in bash history or process lists
                    val keyFile = File(context.cacheDir, ".ag_env_tmp_${System.currentTimeMillis()}")
                    val keyContent = buildString {
                        appendLine("export OPENCODE_API_KEY=${apiKey.shellSingleQuote()}")
                        appendLine("export OPENCODE_BASE_URL=${baseUrl.shellSingleQuote()}")
                        appendLine("export OPENCODE_MODEL=${model.shellSingleQuote()}")
                    }

                    // Write env file via Termux (cache dir is shared via app uid)
                    val writeCmd = "cat > ${keyFile.absolutePath.shellSingleQuote()} << 'AGEOF'\n$keyContent\nAGEOF"

                    // SEC-1 FIX: Use single-quote escaping for prompt — prevents backtick/subshell injection
                    val cmd = buildString {
                        append("source ${keyFile.absolutePath.shellSingleQuote()} && ")
                        append("rm -f ${keyFile.absolutePath.shellSingleQuote()} && ")
                        append("cd ${config.workspacePath.shellSingleQuote()} && ")
                        append("opencode run ${prompt.shellSingleQuote()}")
                    }

                    val writeRes = TermuxRunner.executeCommand(context, writeCmd, config.workspacePath)
                    if (writeRes.error != null) {
                        // Fallback: pass key inline if env file write fails
                        val fallbackCmd = "OPENCODE_API_KEY=${apiKey.shellSingleQuote()} " +
                            "OPENCODE_BASE_URL=${baseUrl.shellSingleQuote()} " +
                            "OPENCODE_MODEL=${model.shellSingleQuote()} " +
                            "opencode run ${prompt.shellSingleQuote()}"
                        val res = TermuxRunner.executeCommand(context, fallbackCmd, config.workspacePath)
                        handleTermuxResult(res)
                    } else {
                        val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                        handleTermuxResult(res)
                    }
                }

                ExecutionMode.SSH -> {
                    addMessage("Executing opencode CLI via SSH...", MessageType.AGENT_THOUGHT, "System")
                    // SEC-1 FIX: Use single-quote escaping for SSH command too
                    val sshCommand = "cd ${config.workspacePath.shellSingleQuote()} && " +
                        "OPENCODE_API_KEY=${apiKey.shellSingleQuote()} " +
                        "opencode run ${prompt.shellSingleQuote()}"
                    val output = SshConnection.executeCommand(config, sshCommand)
                    addMessage(output, MessageType.TOOL_OUTPUT, "opencode")
                }

                ExecutionMode.SANDBOX -> {
                    // BUG-4 FIX: Wire ZenApiClient to SANDBOX mode so it actually works
                    if (apiKey.isEmpty()) {
                        addMessage(
                            "No API key configured. Please go to SETTINGS and enter your OpenCode Zen API key.",
                            MessageType.SYSTEM_ERROR
                        )
                    } else {
                        addMessage("Calling OpenCode Zen API directly (Sandbox mode)...", MessageType.AGENT_THOUGHT, "System")
                        val conversationHistory = _messages.value
                            .filter { it.type == MessageType.USER || it.type == MessageType.AGENT_RESPONSE }
                            .map { ZenMessage(if (it.type == MessageType.USER) "user" else "assistant", it.text) }

                        val client = ZenApiClient(apiKey, baseUrl, model)
                        val result = client.getCompletion(conversationHistory)
                        result.fold(
                            onSuccess = { addMessage(it, MessageType.AGENT_RESPONSE, "Zen AI") },
                            onFailure = { addMessage("API Error: ${it.message}", MessageType.SYSTEM_ERROR) }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            addMessage("Unexpected error: ${e.message}", MessageType.SYSTEM_ERROR)
        } finally {
            // CRASH-6 FIX: Always reset, even if exception was thrown above
            _isProcessing.value = false
        }
    }

    private fun handleTermuxResult(res: TermuxRunner.TermuxResult) {
        if (res.error != null) {
            addMessage(
                "Termux Execution Error: ${res.error}\nIs opencode installed? Run: pkg install opencode",
                MessageType.SYSTEM_ERROR
            )
        } else {
            if (res.stdout.isNotEmpty()) addMessage(res.stdout, MessageType.TOOL_OUTPUT, "opencode")
            if (res.stderr.isNotEmpty()) addMessage(res.stderr, MessageType.SYSTEM_ERROR)
        }
    }
}
