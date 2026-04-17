package com.chatfolio.domain.usecase

import com.chatfolio.domain.port.AgentTool
import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.ChatRole
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmResponse
import com.chatfolio.domain.port.LlmTool
import timber.log.Timber

/**
 * Generic agent loop that keeps re-firing the LLM until it stops
 * requesting data tools, or the safety cap is reached.
 *
 * Data tools are "read-only" — the LLM calls them to GET information,
 * the executor fetches it, and the result is fed back into the conversation.
 *
 * Action tools (addTransaction, deleteTransaction, etc.) are NOT executed
 * inside the loop. They pass through to the caller for side-effect handling.
 *
 * @param llmEngine The LLM provider abstraction
 * @param messages Mutable conversation history — grows as the loop runs
 * @param dataTools Tools whose results are fed back to the LLM (loop continues)
 * @param actionToolSchemas Tool schemas for side-effect tools (loop exits, caller handles)
 * @param maxSteps Maximum number of data-fetching iterations before forced exit
 * @return The final LlmResponse (may contain text, action tool calls, or both)
 */
suspend fun agentLoop(
    llmEngine: LlmEngine,
    messages: MutableList<ChatMessage>,
    dataTools: List<AgentTool>,
    actionToolSchemas: List<LlmTool>,
    maxSteps: Int = 5,
): LlmResponse {
    val allSchemas = dataTools.map { it.schema } + actionToolSchemas
    val dataToolNames = dataTools.map { it.schema.name }.toSet()
    val executorMap = dataTools.associate { it.schema.name to it.execute }

    var stepCount = 0
    var response = llmEngine.sendMessage(messages, allSchemas)

    while (stepCount < maxSteps) {
        val pendingDataCalls = response.toolCalls?.filter { it.name in dataToolNames }

        // EXIT: No data tool calls → the LLM is done researching
        if (pendingDataCalls.isNullOrEmpty()) break

        Timber.d("Agent loop step %d: executing %d data tool(s)", stepCount + 1, pendingDataCalls.size)

        // Execute each data tool the LLM requested
        for (call in pendingDataCalls) {
            val executor = executorMap[call.name] ?: continue
            val result = executor(call.arguments)

            // Append the exchange to conversation history
            messages += ChatMessage(role = ChatRole.TOOL_CALL, functionCall = call)
            messages += ChatMessage(role = ChatRole.TOOL_RESULT, functionResponseName = call.name, functionResponse = result)
        }

        // Re-fire LLM with enriched history
        response = llmEngine.sendMessage(messages, allSchemas)
        stepCount++
    }

    if (stepCount >= maxSteps) {
        Timber.w("Agent loop hit maxSteps cap (%d). Returning partial response.", maxSteps)
    }

    return response
}
