package com.example.term_project
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Response
import retrofit2.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class AiService {
    private val apiKey = BuildConfig.UPSTAGE_API_KEY
    private val baseUrl = "https://api.upstage.ai/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(UpstageApiService::class.java)

    suspend fun generateTitle(diaryContent: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(
                Message("system", "일기 내용을 바탕으로 적절한 제목을 생성해주세요. 10자 이내로 간단명료하게."),
                Message("user", diaryContent)
            )

            val request = CompletionRequest(
                model = "solar-1-mini-chat",
                messages = messages,
                max_tokens = 50,
                temperature = 0.7
            )

            val response = apiService.generateCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: "새로운 일기"
            } else {
                "새로운 일기"
            }
        } catch (e: Exception) {
            "새로운 일기"
        }
    }

    suspend fun getAiSuggestion(diaryContent: String, customPrompt: String? = null): String =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Log.d("AiService", "Starting AI request...")

                val systemMessage = if (customPrompt.isNullOrBlank()) {
                    "사용자가 작성한 일기를 바탕으로 일기내용에 공감하거나, 조언하거나, 위로해주세요. 한국어로 답변하고 최대 200자 이내로 친근하고 다정한 말투로 작성해주세요."
                } else {
                    "사용자의 일기를 바탕으로 질문에 답변해주세요. 영어로 따뜻하고 친근한 말투로 답변해주세요."
                }

                val userContent = if (customPrompt.isNullOrBlank()) {
                    "일기 내용: $diaryContent"
                } else {
                    "일기 내용: $diaryContent\n\n질문: $customPrompt"
                }

                val messages = listOf(
                    Message("system", systemMessage),
                    Message("user", userContent)
                )

                val request = CompletionRequest(
                    model = "solar-1-mini-chat",
                    messages = messages,
                    max_tokens = 200,
                    temperature = 0.7
                )

                Log.d("AiService", "Sending request to API...")
                val response = apiService.generateCompletion("Bearer $apiKey", request)

                Log.d("AiService", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val result = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                        ?: "죄송합니다. 잠시 후 다시 시도해주세요."
                    Log.d("AiService", "Success: $result")
                    result
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AiService", "API Error: ${response.code()} - $errorBody")
                    "AI 서비스에 일시적인 문제가 있습니다. (${response.code()})"
                }
            } catch (e: Exception) {
                Log.e("AiService", "Exception occurred", e)
                "네트워크 오류가 발생했습니다: ${e.message}"
            }
        }
}

// API 인터페이스와 데이터 클래스들
interface UpstageApiService {
    @POST("v1/solar/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun generateCompletion(
        @Header("Authorization") authorization: String,
        @Body request: CompletionRequest
    ): Response<CompletionResponse>
}

data class CompletionRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int,
    val temperature: Double
)

data class Message(
    val role: String,
    val content: String
)

data class CompletionResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)