package com.example.term_project

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream


class T5TitleGenerator(private val context: Context) {
    private var model: Module? = null
    private var vocabulary: Map<String, Int>? = null
    private var reverseVocabulary: Map<Int, String>? = null

    companion object {
        private const val MAX_INPUT_LENGTH = 512
        private const val MAX_OUTPUT_LENGTH = 50
        private const val PAD_TOKEN = "<pad>"
        private const val EOS_TOKEN = "</s>"
        private const val UNK_TOKEN = "<unk>"
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        try {
            // 모델 로드
            val modelFile = getAssetFile("my_quantized_t5_mobile.ptl")
            model = LiteModuleLoader.load(modelFile.getAbsolutePath())
            //model = Module.load(modelFile.absolutePath)

            // Vocabulary 로드
            vocabulary = loadVocabulary("vocabulary.json")
            reverseVocabulary = loadReverseVocabulary("reverseVocabulary.json")

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("모델 초기화 실패: ${e.message}")
        }
    }

    suspend fun generateTitle(diaryContent: String): String = withContext(Dispatchers.IO) {
        try {
            val model = this@T5TitleGenerator.model
                ?: return@withContext "새로운 일기"
            val vocab = vocabulary ?: return@withContext "새로운 일기"
            val reverseVocab = reverseVocabulary ?: return@withContext "새로운 일기"

            // 입력 텍스트 전처리 및 토크나이징
            val inputText = "summarize: $diaryContent"
            val inputTokens = tokenize(inputText, vocab)

            println("Input tokens size: ${inputTokens.size}")
            println("First 10 tokens: ${inputTokens.take(10)}")

            // 여러 텐서 타입 시도
            val output = try {
                // Long 타입으로 시도
                val inputArray = inputTokens.map { it.toLong() }.toLongArray()
                val inputTensor = Tensor.fromBlob(
                    inputArray,
                    longArrayOf(1, inputTokens.size.toLong())
                )
                println("Created Long tensor successfully")
                model.forward(IValue.from(inputTensor))
            } catch (e1: Exception) {
                println("Long tensor failed: ${e1.message}")
                try {
                    // Float 타입으로 시도
                    val inputArray = inputTokens.map { it.toFloat() }.toFloatArray()
                    val inputTensor = Tensor.fromBlob(
                        inputArray,
                        longArrayOf(1, inputTokens.size.toLong())
                    )
                    println("Created Float tensor successfully")
                    model.forward(IValue.from(inputTensor))
                } catch (e2: Exception) {
                    println("Float tensor failed: ${e2.message}")
                    // Int 타입으로 시도
                    val inputArray = inputTokens.toIntArray()
                    val inputTensor = Tensor.fromBlob(
                        inputArray,
                        longArrayOf(1, inputTokens.size.toLong())
                    )
                    println("Created Int tensor successfully")
                    model.forward(IValue.from(inputTensor))
                }
            }

            println("Model forward successful")

            // 출력 처리
            val outputTokens = when {
                output.isTensor -> {
                    val tensor = output.toTensor()
                    println("Output tensor shape: ${tensor.shape().contentToString()}")
                    when {
                        tensor.dtype() == org.pytorch.DType.FLOAT32 ->
                            tensor.dataAsFloatArray.map { it.toLong() }
                        tensor.dtype() == org.pytorch.DType.INT64 ->
                            tensor.dataAsLongArray.toList()
                        tensor.dtype() == org.pytorch.DType.INT32 ->
                            tensor.dataAsIntArray.map { it.toLong() }
                        else -> {
                            println("Unknown tensor dtype: ${tensor.dtype()}")
                            listOf()
                        }
                    }
                }
                output.isTuple -> {
                    // T5는 때때로 tuple을 반환할 수 있음
                    val tuple = output.toTuple()
                    val firstElement = tuple[0]
                    if (firstElement.isTensor) {
                        val tensor = firstElement.toTensor()
                        when {
                            tensor.dtype() == org.pytorch.DType.FLOAT32 ->
                                tensor.dataAsFloatArray.map { it.toLong() }
                            tensor.dtype() == org.pytorch.DType.INT64 ->
                                tensor.dataAsLongArray.toList()
                            tensor.dtype() == org.pytorch.DType.INT32 ->
                                tensor.dataAsIntArray.map { it.toLong() }
                            else -> listOf()
                        }
                    } else {
                        listOf()
                    }
                }
                else -> {
                    println("Unknown output type")
                    listOf()
                }
            }

            println("Output tokens: ${outputTokens.take(20)}")

            // 디토크나이징
            val generatedTitle = detokenize(outputTokens, reverseVocab)
            println("Generated title before post-processing: '$generatedTitle'")

            // 후처리
            return@withContext postProcessTitle(generatedTitle)

        } catch (e: Exception) {
            e.printStackTrace()
            println("Error in generateTitle: ${e.message}")
            return@withContext "새로운 일기"
        }
    }

    private fun tokenize(text: String, vocabulary: Map<String, Int>): List<Int> {
        // 간단한 토크나이징 (실제로는 T5Tokenizer와 동일한 방식 사용)
        val tokens = text.lowercase()
            .replace(Regex("[^a-zA-Z가-힣0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }

        val tokenIds = mutableListOf<Int>()

        for (token in tokens) {
            val tokenId = vocabulary[token] ?: vocabulary[UNK_TOKEN] ?: 0
            tokenIds.add(tokenId)
        }

        // 패딩 처리
        while (tokenIds.size < MAX_INPUT_LENGTH) {
            tokenIds.add(vocabulary[PAD_TOKEN] ?: 0)
        }

        // 최대 길이 제한
        if (tokenIds.size > MAX_INPUT_LENGTH) {
            return tokenIds.take(MAX_INPUT_LENGTH)
        }

        return tokenIds
    }

    private fun detokenize(tokenIds: List<Long>, reverseVocabulary: Map<Int, String>): String {
        val tokens = mutableListOf<String>()

        for (tokenId in tokenIds) {
            val token = reverseVocabulary[tokenId.toInt()]
            if (token != null && token != PAD_TOKEN && token != EOS_TOKEN) {
                tokens.add(token)
            } else if (token == EOS_TOKEN) {
                break
            }
        }

        return tokens.joinToString(" ")
    }

    private fun postProcessTitle(title: String): String {
        var processedTitle = title.trim()

        // 불필요한 토큰 제거
        processedTitle = processedTitle
            .replace(Regex("^(summarize:|title:|제목:)\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 길이 제한 (10자 이내)
        if (processedTitle.length > 10) {
            processedTitle = processedTitle.take(10)
        }

        // 빈 제목 처리
        if (processedTitle.isEmpty() || processedTitle.isBlank()) {
            return "새로운 일기"
        }

        return processedTitle
    }

    private fun loadVocabulary(fileName: String): Map<String, Int> {
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val vocabulary = mutableMapOf<String, Int>()

            jsonObject.keys().forEach { key ->
                vocabulary[key] = jsonObject.getInt(key)
            }

            vocabulary
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun loadReverseVocabulary(fileName: String): Map<Int, String> {
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val reverseVocabulary = mutableMapOf<Int, String>()

            jsonObject.keys().forEach { key ->
                reverseVocabulary[key.toInt()] = jsonObject.getString(key)
            }

            reverseVocabulary
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun getAssetFile(fileName: String): File {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            context.assets.open(fileName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file
    }
}


class DiaryTitleService(private val context: Context) {
    private val titleGenerator = T5TitleGenerator(context)
    private var isInitialized = false

    suspend fun initializeIfNeeded() {
        if (!isInitialized) {
            titleGenerator.initializeModel()
            isInitialized = true
        }
    }

    suspend fun generateTitle(diaryContent: String): String {
        initializeIfNeeded()
        return titleGenerator.generateTitle(diaryContent)
    }
}