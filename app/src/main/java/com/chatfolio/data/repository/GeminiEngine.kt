package com.chatfolio.data.repository

import com.chatfolio.data.network.withRetry
import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmResponse
import com.chatfolio.domain.port.LlmTool
import com.chatfolio.domain.port.LlmToolParameter
import com.chatfolio.domain.port.ToolCall
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Part
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiEngine
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : LlmEngine {
        override suspend fun sendMessage(
            messages: List<ChatMessage>,
            tools: List<LlmTool>,
        ): LlmResponse {
            // Dynamically map our generic tools to Google Jetpack GenAI FunctionDeclarations
            val geminiDeclarations =
                tools.map { tool ->
                    val parameters = tool.parameters.map { mapSchema(it) }
                    val required = tool.parameters.map { it.name }

                    FunctionDeclaration(
                        name = tool.name,
                        description = tool.description,
                        parameters = parameters,
                        requiredParameters = required,
                    )
                }

            val geminiTool = if (geminiDeclarations.isNotEmpty()) Tool(geminiDeclarations) else null
            val toolList = if (geminiTool != null) listOf(geminiTool) else emptyList()

            val apiKey = settingsRepository.getGeminiApiKey()
            if (apiKey.isNullOrBlank()) {
                return LlmResponse(textResponse = "Error: Please enter your Gemini API Key in the Settings screen.", toolCalls = null)
            }

            // Create a model instance for this specific request and toolset
            val generativeModel =
                GenerativeModel(
                    modelName = settingsRepository.getGeminiModelName(),
                    apiKey = apiKey,
                    tools = toolList,
                    systemInstruction =
                        Content(
                            role = "system",
                            parts =
                                listOf(
                                    TextPart(
                                        "You are an expert financial portfolio assistant. If the user provides multiple transactions in a single message, " +
                                            "YOU MUST CALL THE addTransaction FUNCTION MULTIPLE TIMES IN PARALLEL for each individual asset mentioned.\n" +
                                            "You can freely engage in ordinary conversation, answer financial inquiries, " +
                                            "and provide market insights in plain text alongside your tool calls. " +
                                            "DO NOT invoke tools unless the user explicitly commands a portfolio action.",
                                    ),
                                ),
                        ),
                    generationConfig =
                        generationConfig {
                            temperature = 0.7f
                        },
                )
            try {
                val response =
                    withRetry(
                        times = 2,
                        shouldRetry = { error ->
                            val cause = error.cause
                            val isKtorServerErr =
                                (cause is io.ktor.client.plugins.ServerResponseException) &&
                                    (cause.response.status.value in 500..504)

                            // GenerativeAI SDK usually wraps HTTP 5xx inside ServerException.
                            val isGoogleServerErr = error is com.google.ai.client.generativeai.type.ServerException
                            val has5xxCode = Regex("""\b(500|502|503|504)\b""").containsMatchIn(error.message ?: "")

                            isKtorServerErr || (isGoogleServerErr && has5xxCode)
                        },
                    ) {
                        // Convert our raw generic messages to Firebase Content objects
                        val firebaseMessages =
                            messages.map { msg ->
                                val parts = mutableListOf<com.google.ai.client.generativeai.type.Part>()
                                if (msg.content != null) {
                                    parts.add(TextPart(msg.content))
                                }
                                if (msg.functionCall != null) {
                                    parts.add(
                                        com.google.ai.client.generativeai.type.FunctionCallPart(
                                            msg.functionCall.name,
                                            msg.functionCall.arguments.mapValues { it.value.toString() },
                                        ),
                                    )
                                }
                                if (msg.functionResponseName != null && msg.functionResponse != null) {
                                    val safeResponse = org.json.JSONObject(msg.functionResponse)
                                    parts.add(com.google.ai.client.generativeai.type.FunctionResponsePart(msg.functionResponseName, safeResponse))
                                }
                                Content(
                                    role = if (msg.role == "model") "model" else "user",
                                    parts = parts,
                                )
                            }

                        // Send to Gemini
                        generativeModel.generateContent(*firebaseMessages.toTypedArray())
                    }

                // Check if Gemini invoked our addTransaction function
                val functionCalls =
                    response.functionCalls.map { fc ->
                        // Custom convert Android JSONObject/Map to standard Map
                        val argsMap = mutableMapOf<String, Any>()
                        // The args is actually a Map in the latest SDK, not org.json.JSONObject
                        fc.args.forEach { (key, value) ->
                            if (value != null) {
                                argsMap[key] = value
                            }
                        }
                        com.chatfolio.domain.port.ToolCall(
                            name = fc.name,
                            arguments = argsMap,
                        )
                    }

                var textOutput: String? = null
                try {
                    textOutput = response.text
                } catch (e: Exception) {
                    // ignore
                }

                return LlmResponse(
                    textResponse = textOutput,
                    toolCalls = if (functionCalls.isEmpty()) null else functionCalls,
                )
            } catch (e: Exception) {
                return LlmResponse(
                    textResponse = "Error connecting to Gemini: ${e.message}",
                    toolCalls = null,
                )
            }
        }

        private fun mapSchema(param: LlmToolParameter): Schema<out Any> {
            return when (param.type.uppercase()) {
                "STRING" -> Schema.str(param.name, param.description)
                "NUMBER" -> Schema.double(param.name, param.description)
                else -> Schema.str(param.name, param.description)
            }
        }
    }
