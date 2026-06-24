package com.antigravity.vibecoder.data

enum class Provider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    GITHUB_MODELS("GitHub Models", "https://models.inference.ai.azure.com", "gpt-4o"),
    OLLAMA("Ollama", "http://localhost:11434/v1", "qwen2.5-coder:7b"),
    OPENCLAUDE("OpenClaude", "", "")
}
