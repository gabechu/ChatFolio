package com.chatfolio.domain.port

data class ChatMessage(
    val role: String, // "user", "model", or "system"
    val content: String
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any>
)

data class LlmToolParameter(
    val name: String,
    val type: String, // e.g. "STRING", "NUMBER"
    val description: String
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: List<LlmToolParameter>
)

data class LlmResponse(
    val textResponse: String?,
    val toolCalls: List<ToolCall>?
)

interface LlmEngine {
    /**
     * Send a list of messages to the LLM and wait for its response, optionally with tools available.
     */
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        tools: List<LlmTool> = emptyList()
    ): LlmResponse
}
