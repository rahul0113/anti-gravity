package com.antigravity.vibecoder.model

import java.util.UUID

enum class MessageType {
    USER,
    AGENT_THOUGHT,
    AGENT_RESPONSE,
    TOOL_CALL,
    TOOL_OUTPUT,
    SYSTEM_INFO,
    SYSTEM_ERROR
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)
