package com.antigravity.vibecoder.model

enum class ExecutionMode {
    SANDBOX,
    SSH,
    TERMUX_SERVICE
}

data class ConnectionConfig(
    val executionMode: ExecutionMode = ExecutionMode.SANDBOX,
    val host: String = "127.0.0.1",
    val port: Int = 8022,
    val user: String = "android",
    val authType: AuthType = AuthType.PASSWORD,
    val passwordKey: String = "",
    val workspacePath: String = "/data/data/com.termux/files/home"
) {
    enum class AuthType {
        PASSWORD,
        PRIVATE_KEY
    }
}
