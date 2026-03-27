package com.chatfolio.data.repository

import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmResponse
import com.chatfolio.domain.port.LlmTool
import com.chatfolio.domain.port.LlmToolParameter
import com.chatfolio.domain.port.ToolCall
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiEngine @Inject constructor(
    private val settingsRepository: SettingsRepository
) : LlmEngine {

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        tools: List<LlmTool>
    ): LlmResponse {
        // Dynamically map our generic tools to Google Jetpack GenAI FunctionDeclarations
        val geminiDeclarations = tools.map { tool ->
            val parameters = tool.parameters.map { mapSchema(it) }
            val required = tool.parameters.map { it.name }
            
            FunctionDeclaration(
                name = tool.name,
                description = tool.description,
                parameters = parameters,
                requiredParameters = required
            )
        }

        val geminiTool = if (geminiDeclarations.isNotEmpty()) Tool(geminiDeclarations) else null
        val toolList = if (geminiTool != null) listOf(geminiTool) else emptyList()

        val apiKey = settingsRepository.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return LlmResponse(textResponse = "Error: Please enter your Gemini API Key in the Settings screen.", toolCalls = null)
        }

        // Create a model instance for this specific request and toolset
        val generativeModel = GenerativeModel(
            modelName = settingsRepository.getGeminiModelName(),
            apiKey = apiKey,
            tools = toolList,
            systemInstruction = Content(role = "system", parts = listOf(TextPart("You are an expert financial portfolio assistant. If the user provides multiple transactions in a single message, YOU MUST CALL THE addTransaction FUNCTION MULTIPLE TIMES IN PARALLEL for each individual asset mentioned."))),
            generationConfig = generationConfig {
                temperature = 0.2f
            }
        )
        try {
            // Convert our raw generic messages to Firebase Content objects
            val firebaseMessages = messages.map { msg ->
                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(TextPart(msg.content))
                )
            }

            // Send to Gemini
            val response = generativeModel.generateContent(*firebaseMessages.toTypedArray())

            // Check if Gemini invoked our addTransaction function
            val functionCalls = response.functionCalls.map { fc ->
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
                    arguments = argsMap
                )
            }

            return LlmResponse(
                textResponse = response.text,
                toolCalls = if (functionCalls.isEmpty()) null else functionCalls
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return LlmResponse(
                textResponse = "Error connecting to Gemini: ${e.message}",
                toolCalls = null
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
