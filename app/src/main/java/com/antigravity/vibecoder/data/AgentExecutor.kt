package com.antigravity.vibecoder.data

import android.content.Context
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        
        // Escape prompt to safely pass into bash
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\$", "\\\$")
        
        val envSetup = "export OPENCODE_API_KEY=\"\$OPENCODE_API_KEY\" && export OPENCODE_BASE_URL=\"\$OPENCODE_BASE_URL\" && export OPENCODE_MODEL=\"\$OPENCODE_MODEL\""
        
        when (config.executionMode) {
            ExecutionMode.TERMUX_SERVICE -> {
                val cmd = "export OPENCODE_API_KEY=\"$apiKey\" && export OPENCODE_BASE_URL=\"$baseUrl\" && export OPENCODE_MODEL=\"$model\" && opencode run \"$escapedPrompt\""
                
                addMessage("Executing opencode CLI natively via Termux IPC...", MessageType.AGENT_THOUGHT, "System")
                val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                
                if (res.error != null) {
                    addMessage("Termux Execution Error: ${res.error}\nIs opencode installed in Termux?", MessageType.SYSTEM_ERROR)
                } else {
                    if (res.stdout.isNotEmpty()) {
                        addMessage(res.stdout, MessageType.TOOL_OUTPUT, "opencode")
                    }
                    if (res.stderr.isNotEmpty()) {
                        addMessage(res.stderr, MessageType.SYSTEM_ERROR)
                    }
                }
            }
            ExecutionMode.SSH -> {
                val sshCommand = "cd ${config.workspacePath} && export OPENCODE_API_KEY=\"$apiKey\" && opencode run \"$escapedPrompt\""
                addMessage("Executing opencode CLI via SSH...", MessageType.AGENT_THOUGHT, "System")
                val output = SshConnection.executeCommand(config, sshCommand)
                addMessage(output, MessageType.TOOL_OUTPUT, "opencode")
            }
            ExecutionMode.SANDBOX -> {
                addMessage("OpenCode CLI is not supported in internal SANDBOX mode. Please switch to TERMUX_SERVICE and install opencode.", MessageType.SYSTEM_ERROR)
            }
        }
        
        _isProcessing.value = false
    }
}
