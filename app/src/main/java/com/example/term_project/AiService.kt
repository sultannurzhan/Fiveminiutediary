package com.example.term_project

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiService {
    private val apiKey = ""
    private val baseUrl = "https://api.groq.com/openai/v1/chat/completions"
    
    suspend fun generateTitle(diaryContent: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Based on the following diary content, generate an appropriate title in English. 
                The title should be concise, emotional, and within 10 words.
                Respond ONLY in English language, regardless of the input language.
                
                Diary content:
                $diaryContent
                
                Please respond with only the title in English.
            """.trimIndent()
            
            return@withContext callGroqApi(prompt)
        } catch (e: Exception) {
            return@withContext "Today's Diary"
        }
    }
    
    suspend fun getAiSuggestion(diaryContent: String, customPrompt: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val prompt = if (customPrompt.isNullOrBlank()) {
                """
                This is a diary entry written by a user. Please read it and provide advice in English including:
                
                1. Emotional empathy and comfort
                2. Positive perspective
                3. Practical advice or solutions
                4. Encouraging message
                
                Diary content:
                $diaryContent
                
                Please respond ONLY in English language with a warm and friendly tone. Keep it around 150-200 words.
                Do not use any other language except English in your response.
                """.trimIndent()
            } else {
                """
                Here is a diary entry written by a user:
                $diaryContent
                
                User's question: $customPrompt
                
                Based on the diary content above, please answer the user's question in English with a warm and friendly tone.
                Respond ONLY in English language, regardless of the input language.
                """.trimIndent()
            }
            
            return@withContext callGroqApi(prompt)
        } catch (e: Exception) {
            return@withContext "Sorry, there was a temporary issue with the AI service. Please try again later. Error: ${e.message}"
        }
    }
    
    suspend fun extendDiary(diaryContent: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Please read this diary entry and extend it naturally. Add more thoughts, emotions, details, or reflections that would make the diary more complete and engaging. The extension should feel like a natural continuation of the original content.
                
                Guidelines:
                - Keep the same tone and writing style
                - Add meaningful details or emotions
                - Make it feel like the same person writing
                - Don't repeat the existing content
                - Respond ONLY in English language
                - Keep the extension around 100-150 words
                
                Original diary content:
                $diaryContent
                
                Please provide only the extension part that can be added to the original diary.
            """.trimIndent()
            
            return@withContext callGroqApi(prompt)
        } catch (e: Exception) {
            return@withContext "Sorry, there was an error extending your diary. Please try again later."
        }
    }

    private fun callGroqApi(prompt: String): String {
        val url = URL(baseUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true
        connection.connectTimeout = 30000 // 30 seconds
        connection.readTimeout = 30000 // 30 seconds
        
        val requestBody = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful and empathetic AI assistant. Always respond in English only, regardless of the language of the input. Provide warm, friendly, and supportive advice.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 1000)
            put("temperature", 0.7)
        }
        
        try {
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            Log.d("AiService", "API Response Code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("AiService", "API Response: $response")
                val jsonResponse = JSONObject(response)
                return jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else {
                // Read error response for debugging
                val errorResponse = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                } catch (e: Exception) {
                    "Could not read error response"
                }
                Log.e("AiService", "API Error Response: $errorResponse")
                throw Exception("API call failed with response code: $responseCode. Error: $errorResponse")
            }
        } catch (e: Exception) {
            throw Exception("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
}
