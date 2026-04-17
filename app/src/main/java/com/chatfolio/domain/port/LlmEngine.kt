package com.chatfolio.domain.port

/**
 * Vendor-neutral role constants for conversation messages.
 * Each LlmEngine implementation maps these to provider-specific values
 * (e.g., Gemini uses "model" for ASSISTANT, OpenAI uses "assistant").
 */
object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL_CALL = "tool_call"
    const val TOOL_RESULT = "tool_result"
}

data class ChatMessage(
    // Use ChatRole.* constants for vendor-neutral role assignment
    val role: String,
    val content: String? = null,
    val functionCall: ToolCall? = null,
    val functionResponseName: String? = null,
    val functionResponse: Map<String, Any>? = null,
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any>,
)

data class LlmToolParameter(
    val name: String,
    // e.g. "STRING", "NUMBER"
    val type: String,
    val description: String,
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: List<LlmToolParameter>,
)

/**
 * A tool the LLM can call that carries its own executor.
 * Used for "data tools" in the agent loop — the LLM requests data,
 * the executor fetches it, and the result is fed back to the LLM.
 */
data class AgentTool(
    val schema: LlmTool,
    val execute: suspend (Map<String, Any>) -> Map<String, Any>,
)

data class LlmResponse(
    val textResponse: String?,
    val toolCalls: List<ToolCall>?,
)

interface LlmEngine {
    /**
     * Send a list of messages to the LLM and wait for its response, optionally with tools available.
     */
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        tools: List<LlmTool> = emptyList(),
    ): LlmResponse
}
