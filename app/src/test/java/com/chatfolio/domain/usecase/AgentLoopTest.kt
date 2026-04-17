package com.chatfolio.domain.usecase

import com.chatfolio.domain.port.AgentTool
import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.ChatRole
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmResponse
import com.chatfolio.domain.port.LlmTool
import com.chatfolio.domain.port.LlmToolParameter
import com.chatfolio.domain.port.ToolCall
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AgentLoopTest {
    private lateinit var llmEngine: LlmEngine

    private val dummyDataTool =
        AgentTool(
            schema =
                LlmTool(
                    name = "searchData",
                    description = "Searches for data",
                    parameters = listOf(LlmToolParameter("query", "STRING", "The search query")),
                ),
            execute = { args ->
                mapOf("result" to "found 3 items for '${args["query"]}'")
            },
        )

    private val dummyActionTool =
        LlmTool(
            name = "doAction",
            description = "Performs a side effect",
            parameters = listOf(LlmToolParameter("target", "STRING", "The action target")),
        )

    @Before
    fun setup() {
        llmEngine = mockk(relaxed = true)
    }

    @Test
    fun `single data tool call - executes tool and returns final text`() =
        runTest {
            // First call: LLM requests data tool
            // Second call: LLM returns text after receiving data
            coEvery { llmEngine.sendMessage(any(), any()) } returnsMany
                listOf(
                    LlmResponse(
                        textResponse = null,
                        toolCalls = listOf(ToolCall("searchData", mapOf("query" to "AAPL"))),
                    ),
                    LlmResponse(
                        textResponse = "Found 3 items for AAPL.",
                        toolCalls = null,
                    ),
                )

            val messages = mutableListOf(ChatMessage(ChatRole.USER, content = "search for AAPL"))
            val result = agentLoop(llmEngine, messages, listOf(dummyDataTool), listOf(dummyActionTool))

            assertThat(result.textResponse).isEqualTo("Found 3 items for AAPL.")
            assertThat(result.toolCalls).isNull()
            // History should contain: original message + tool_call + tool_result
            assertThat(messages).hasSize(3)
            assertThat(messages[1].role).isEqualTo(ChatRole.TOOL_CALL)
            assertThat(messages[2].role).isEqualTo(ChatRole.TOOL_RESULT)
            coVerify(exactly = 2) { llmEngine.sendMessage(any(), any()) }
        }

    @Test
    fun `multi-step - LLM calls data tool across two iterations`() =
        runTest {
            // Step 1: LLM requests first search
            // Step 2: LLM requests second search
            // Step 3: LLM returns final text
            coEvery { llmEngine.sendMessage(any(), any()) } returnsMany
                listOf(
                    LlmResponse(
                        textResponse = null,
                        toolCalls = listOf(ToolCall("searchData", mapOf("query" to "AAPL"))),
                    ),
                    LlmResponse(
                        textResponse = null,
                        toolCalls = listOf(ToolCall("searchData", mapOf("query" to "MSFT"))),
                    ),
                    LlmResponse(
                        textResponse = "AAPL had 3, MSFT had 5.",
                        toolCalls = null,
                    ),
                )

            val messages = mutableListOf(ChatMessage(ChatRole.USER, content = "compare AAPL and MSFT"))
            val result = agentLoop(llmEngine, messages, listOf(dummyDataTool), listOf(dummyActionTool))

            assertThat(result.textResponse).isEqualTo("AAPL had 3, MSFT had 5.")
            // original + (tool_call + tool_result) * 2 = 5
            assertThat(messages).hasSize(5)
            coVerify(exactly = 3) { llmEngine.sendMessage(any(), any()) }
        }

    @Test
    fun `maxSteps enforcement - loop exits at cap`() =
        runTest {
            // LLM keeps requesting the data tool indefinitely
            coEvery { llmEngine.sendMessage(any(), any()) } returns
                LlmResponse(
                    textResponse = null,
                    toolCalls = listOf(ToolCall("searchData", mapOf("query" to "infinite"))),
                )

            val messages = mutableListOf(ChatMessage(ChatRole.USER, content = "loop forever"))
            val result = agentLoop(llmEngine, messages, listOf(dummyDataTool), listOf(dummyActionTool), maxSteps = 3)

            // Should have called sendMessage 4 times: initial + 3 retries
            coVerify(exactly = 4) { llmEngine.sendMessage(any(), any()) }
            // The result still has tool calls since maxSteps forced exit
            assertThat(result.toolCalls).isNotNull()
        }

    @Test
    fun `no data tools - loop exits immediately with action tools`() =
        runTest {
            // LLM only returns an action tool — loop should NOT iterate
            coEvery { llmEngine.sendMessage(any(), any()) } returns
                LlmResponse(
                    textResponse = null,
                    toolCalls = listOf(ToolCall("doAction", mapOf("target" to "buy AAPL"))),
                )

            val messages = mutableListOf(ChatMessage(ChatRole.USER, content = "buy AAPL"))
            val result = agentLoop(llmEngine, messages, listOf(dummyDataTool), listOf(dummyActionTool))

            assertThat(result.toolCalls).hasSize(1)
            assertThat(result.toolCalls!![0].name).isEqualTo("doAction")
            // Only the original message — no tool exchanges appended
            assertThat(messages).hasSize(1)
            coVerify(exactly = 1) { llmEngine.sendMessage(any(), any()) }
        }

    @Test
    fun `mixed response - data tools loop and action tools pass through`() =
        runTest {
            // First call: LLM requests both a data tool AND an action tool
            // The data tool triggers a loop iteration; the action tool is NOT a data tool
            // so on the second call, if only action tools remain, the loop exits
            coEvery { llmEngine.sendMessage(any(), any()) } returnsMany
                listOf(
                    LlmResponse(
                        textResponse = null,
                        toolCalls =
                            listOf(
                                ToolCall("searchData", mapOf("query" to "AAPL")),
                                ToolCall("doAction", mapOf("target" to "buy")),
                            ),
                    ),
                    LlmResponse(
                        textResponse = "Based on 3 transactions, buying now.",
                        toolCalls = listOf(ToolCall("doAction", mapOf("target" to "buy AAPL"))),
                    ),
                )

            val messages = mutableListOf(ChatMessage(ChatRole.USER, content = "analyze and buy AAPL"))
            val result = agentLoop(llmEngine, messages, listOf(dummyDataTool), listOf(dummyActionTool))

            // Final response should have the action tool call + text
            assertThat(result.textResponse).isEqualTo("Based on 3 transactions, buying now.")
            assertThat(result.toolCalls).hasSize(1)
            assertThat(result.toolCalls!![0].name).isEqualTo("doAction")
            coVerify(exactly = 2) { llmEngine.sendMessage(any(), any()) }
        }
}
