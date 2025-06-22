package com.example.term_project

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer

class TokenizerJsonParser(private val tokenizerJson: String) {
    private val tokenizerConfig: JSONObject = JSONObject(tokenizerJson)
    val vocab: Map<String, Int>
    private val reverseVocab: Map<Int, String>
    private val merges: List<Pair<String, String>>
    val addedTokens: Map<String, Int>
    private val reverseAddedTokens: Map<Int, String>
    private val padTokenId: Int
    private val eosTokenId: Int
    private val unkTokenId: Int

    // 메모리 효율을 위한 재사용 가능한 객체들
    private val tempTokenList = mutableListOf<String>()
    private val tempIntList = mutableListOf<Int>()
    private val stringBuilder = StringBuilder()

    init {
        // Model 설정 파싱
        val model = tokenizerConfig.getJSONObject("model")
        val vocabJson = model.getJSONObject("vocab")

        // Vocabulary 구축 (한 번만 생성)
        vocab = buildVocab(vocabJson)
        reverseVocab = vocab.entries.associate { it.value to it.key }

        // Merges 파싱 (메모리 효율적으로)
        merges = buildMerges(model)

        // Added tokens 파싱
        val (addedMap, reverseAddedMap) = buildAddedTokens()
        addedTokens = addedMap
        reverseAddedTokens = reverseAddedMap

        // 특수 토큰 ID 찾기
        padTokenId = findTokenId("<pad>")
        eosTokenId = findTokenId("</s>")
        unkTokenId = findTokenId("<unk>")

        // 초기화 완료 후 임시 객체들 정리
        System.gc()
    }

    private fun buildVocab(vocabJson: JSONObject): Map<String, Int> {
        val vocabMap = HashMap<String, Int>(vocabJson.length())
        vocabJson.keys().forEach { key ->
            vocabMap[key] = vocabJson.getInt(key)
        }
        return vocabMap
    }

    private fun buildMerges(model: JSONObject): List<Pair<String, String>> {
        return if (model.has("merges")) {
            val mergesArray = model.getJSONArray("merges")
            val mergesList = ArrayList<Pair<String, String>>(mergesArray.length())

            for (i in 0 until mergesArray.length()) {
                val mergeParts = mergesArray.getString(i).split(" ", limit = 2)
                if (mergeParts.size == 2) {
                    mergesList.add(Pair(mergeParts[0], mergeParts[1]))
                }
            }
            mergesList
        } else {
            emptyList()
        }
    }

    private fun buildAddedTokens(): Pair<Map<String, Int>, Map<Int, String>> {
        val addedMap = mutableMapOf<String, Int>()
        val reverseAddedMap = mutableMapOf<Int, String>()

        if (tokenizerConfig.has("added_tokens")) {
            val addedTokensArray = tokenizerConfig.getJSONArray("added_tokens")
            for (i in 0 until addedTokensArray.length()) {
                val tokenObj = addedTokensArray.getJSONObject(i)
                val content = tokenObj.getString("content")
                val id = tokenObj.getInt("id")
                addedMap[content] = id
                reverseAddedMap[id] = content
            }
        }

        return Pair(addedMap, reverseAddedMap)
    }

    private fun findTokenId(token: String): Int {
        return addedTokens[token] ?: vocab[token] ?: 0
    }

    fun encode(text: String, maxLength: Int = 512): List<Int> {
        if (text.isEmpty()) return emptyList()

        // 재사용 가능한 리스트 초기화
        tempTokenList.clear()
        tempIntList.clear()

        try {
            println("=== 한글 토큰화 디버깅 ===")
            println("원본 텍스트: '$text'")

            // 1. 정규화 (StringBuilder 재사용)
            val normalizedText = normalizeTextEfficient(text)
            println("정규화 후: '$normalizedText'")

            // 2. Pre-tokenization (메모리 효율적)
            preTokenizeEfficient(normalizedText, tempTokenList)
            println("Pre-tokenization 결과: $tempTokenList")

            // 3. 각 토큰별 처리 과정 확인
            for ((index, token) in tempTokenList.withIndex()) {
                println("토큰 $index: '$token'")

                // BPE 적용
                val bpeTokens = applyBPEEfficient(token)
                println("  BPE 결과: $bpeTokens")

                // Vocabulary lookup
                for (bpeToken in bpeTokens) {
                    val tokenId = when {
                        vocab.containsKey(bpeToken) -> {
                            val id = vocab[bpeToken]!!
                            println("  '$bpeToken' -> vocab[$id]")
                            id
                        }
                        addedTokens.containsKey(bpeToken) -> {
                            val id = addedTokens[bpeToken]!!
                            println("  '$bpeToken' -> addedTokens[$id]")
                            id
                        }
                        else -> {
                            println("  '$bpeToken' -> UNK (${unkTokenId}) - 토큰을 찾을 수 없음!")

                            // 한글인 경우 추가 처리 시도
                            if (bpeToken.any { it in '가'..'힣' }) {
                                val fallbackId = handleKoreanToken(bpeToken)
                                println("  한글 fallback 처리: '$bpeToken' -> $fallbackId")
                                fallbackId
                            } else {
                                unkTokenId
                            }
                        }
                    }

                    tempIntList.add(tokenId)
                    if (tempIntList.size >= maxLength) break
                }
                if (tempIntList.size >= maxLength) break
            }

            println("최종 토큰 ID들: $tempIntList")
            println("=== 토큰화 디버깅 완료 ===")

            // 결과 복사 (원본 리스트는 재사용을 위해 유지)
            return tempIntList.toList()

        } catch (e: Exception) {
            println("Encoding error: ${e.message}")
            e.printStackTrace()
            return listOf(unkTokenId)
        }
    }

    // 한글 토큰 처리 전용 메서드
    private fun handleKoreanToken(koreanToken: String): Int {
        println("    한글 토큰 특별 처리: '$koreanToken'")

        // 1. 유니코드 정규화 시도 (NFC, NFD)
        val nfcToken = Normalizer.normalize(koreanToken, Normalizer.Form.NFC)
        val nfdToken = Normalizer.normalize(koreanToken, Normalizer.Form.NFD)

        if (vocab.containsKey(nfcToken)) {
            println("    NFC 정규화로 찾음: '$nfcToken'")
            return vocab[nfcToken]!!
        }

        if (vocab.containsKey(nfdToken)) {
            println("    NFD 정규화로 찾음: '$nfdToken'")
            return vocab[nfdToken]!!
        }

        // 2. 자모 분해 시도
        try {
            val decomposed = decomposeKorean(koreanToken)
            println("    자모 분해: '$koreanToken' -> '$decomposed'")
            if (vocab.containsKey(decomposed)) {
                return vocab[decomposed]!!
            }
        } catch (e: Exception) {
            println("    자모 분해 실패: ${e.message}")
        }

        // 3. Byte-level 처리 시도
        try {
            val byteTokens = koreanToken.toByteArray(Charsets.UTF_8)
                .map { "Ġ${it.toUByte()}" }

            for (byteToken in byteTokens) {
                if (vocab.containsKey(byteToken)) {
                    println("    Byte-level로 찾음: '$byteToken'")
                    return vocab[byteToken]!!
                }
            }
        } catch (e: Exception) {
            println("    Byte-level 처리 실패: ${e.message}")
        }

        // 4. 문자별 분할 시도
        for (char in koreanToken) {
            val charStr = char.toString()
            if (vocab.containsKey(charStr)) {
                println("    문자별 분할로 찾음: '$charStr'")
                return vocab[charStr]!!
            }

            // ▁ 접두사 시도
            val prefixedChar = "▁$charStr"
            if (vocab.containsKey(prefixedChar)) {
                println("    접두사 추가로 찾음: '$prefixedChar'")
                return vocab[prefixedChar]!!
            }
        }

        println("    모든 시도 실패, UNK 반환")
        return unkTokenId
    }

    // 한글 자모 분해 (간단한 구현)
    private fun decomposeKorean(korean: String): String {
        val result = StringBuilder()

        for (char in korean) {
            if (char in '가'..'힣') {
                // 한글 음절을 자모로 분해
                val code = char.code - 0xAC00
                val jong = code % 28
                val jung = (code - jong) / 28 % 21
                val cho = ((code - jong) / 28 - jung) / 21

                // 초성, 중성, 종성 문자
                val choChar = (0x1100 + cho).toChar()
                val jungChar = (0x1161 + jung).toChar()

                result.append(choChar).append(jungChar)
                if (jong > 0) {
                    val jongChar = (0x11A7 + jong).toChar()
                    result.append(jongChar)
                }
            } else {
                result.append(char)
            }
        }

        return result.toString()
    }

    fun decode(tokenIds: List<Int>): String {
        if (tokenIds.isEmpty()) return ""

        stringBuilder.clear()
        val filteredTokens = mutableListOf<Int>()

        try {
            // 1. 특수 토큰과 반복 토큰 필터링
            var lastTokenId = -1
            var repeatCount = 0

            for (id in tokenIds) {
                // 특수 토큰 건너뛰기
                val token = reverseVocab[id] ?: reverseAddedTokens[id]
                if (token != null) {
                    when {
                        token == "<pad>" || token == "</s>" || token == "<unk>" -> continue
                        token.startsWith("<extra_id_") -> continue // T5 특수 토큰
                        token.startsWith("<") && token.endsWith(">") -> continue // 기타 특수 토큰
                        id == lastTokenId -> {
                            repeatCount++
                            if (repeatCount < 2) { // 최대 1번만 반복 허용
                                filteredTokens.add(id)
                            }
                        }
                        else -> {
                            filteredTokens.add(id)
                            repeatCount = 0
                        }
                    }
                    lastTokenId = id
                }
            }

            // 2. 필터링된 토큰들을 문자열로 변환
            for (id in filteredTokens) {
                val token = reverseVocab[id] ?: reverseAddedTokens[id]
                if (token != null) {
                    stringBuilder.append(token)
                }
            }

            // 3. 후처리
            return postProcessTextEfficient(stringBuilder.toString())

        } catch (e: Exception) {
            println("Decoding error: ${e.message}")
            return ""
        }
    }

    private fun normalizeTextEfficient(text: String): String {
        // T5에 맞는 정규화 (공백 보존)
        return text
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ") // 중복 공백은 하나로
            .trim()
    }

    private fun preTokenizeEfficient(text: String, resultList: MutableList<String>) {
        // T5 토크나이저에 맞는 공백 처리 (▁ 방식)
        var i = 0
        val len = text.length
        stringBuilder.clear()
        var isFirstToken = true

        while (i < len) {
            val char = text[i]

            when {
                char.isWhitespace() -> {
                    if (stringBuilder.isNotEmpty()) {
                        resultList.add(stringBuilder.toString())
                        stringBuilder.clear()
                    }
                    // 공백을 ▁로 표현 (T5/SentencePiece 방식)
                    if (!isFirstToken) {
                        resultList.add("▁")
                    }
                    isFirstToken = false
                }
                char in '가'..'힣' || char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> {
                    if (isFirstToken) {
                        stringBuilder.append("▁") // 첫 번째 단어 앞에 ▁ 추가
                        isFirstToken = false
                    }
                    stringBuilder.append(char)
                }
                else -> {
                    if (stringBuilder.isNotEmpty()) {
                        resultList.add(stringBuilder.toString())
                        stringBuilder.clear()
                    }
                    resultList.add(char.toString())
                    isFirstToken = false
                }
            }
            i++
        }

        if (stringBuilder.isNotEmpty()) {
            resultList.add(stringBuilder.toString())
            stringBuilder.clear()
        }
    }

    private fun applyBPEEfficient(word: String): List<String> {
        if (word.isEmpty() || merges.isEmpty()) return listOf(word)

        // 간단화된 BPE (메모리 효율 우선)
        // 실제 서비스에서는 성능과 정확성의 균형을 맞춤
        val chars = word.toCharArray()
        val tokens = mutableListOf<String>()

        var i = 0
        while (i < chars.size) {
            // 2글자 조합부터 확인
            var found = false
            if (i < chars.size - 1) {
                val bigram = "${chars[i]}${chars[i + 1]}"
                if (vocab.containsKey(bigram)) {
                    tokens.add(bigram)
                    i += 2
                    found = true
                }
            }

            if (!found) {
                tokens.add(chars[i].toString())
                i++
            }
        }

        return tokens
    }

    private fun postProcessTextEfficient(text: String): String {
        return text
            .replace("▁", " ")
            .replace("Ġ", " ")
            .trim()
    }

    // 디버깅용 메서드들
    fun getVocabSize(): Int = vocab.size + addedTokens.size
    fun getPadTokenId(): Int = padTokenId
    fun getEosTokenId(): Int = eosTokenId
    fun getUnkTokenId(): Int = unkTokenId
}

class T5TitleGenerator(private val context: Context) {
    private var model: Module? = null
    private var tokenizer: TokenizerJsonParser? = null

    // 메모리 효율을 위한 재사용 가능한 객체들
    private val tempLongArray = LongArray(512)
    private val tempIntArray = IntArray(512)

    companion object {
        private const val MAX_INPUT_LENGTH = 256  // 길이 단축으로 메모리 절약
        private const val MAX_OUTPUT_LENGTH = 30   // 출력도 단축
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        try {
            // 모델 파일
            val modelFile = getAssetFile("my_quantized_t5_mobile.ptl")

            // 모델 로드
            try {
                model = LiteModuleLoader.load(modelFile.absolutePath)
                println("PyTorch Lite 모델 로드 성공")
            } catch (e: Exception) {
                println("Lite 모델 로드 실패: ${e.message}")
            }

            // TokenizerJsonParser 초기화
            val tokenizerJsonString = context.assets.open("tokenizer.json")
                .bufferedReader().use { it.readText() }
            tokenizer = TokenizerJsonParser(tokenizerJsonString)

            // 초기화 후 GC 실행
            System.gc()

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("모델 초기화 실패: ${e.message}")
        }
    }

    suspend fun generateTitle(diaryContent: String): String = withContext(Dispatchers.IO) {
        try {
            val model = this@T5TitleGenerator.model ?: return@withContext "새로운 일기"
            val tokenizer = this@T5TitleGenerator.tokenizer ?: return@withContext "새로운 일기"

            // 입력 텍스트 길이 제한
            val limitedContent = if (diaryContent.length > 200) {
                diaryContent.take(200)
            } else {
                diaryContent
            }

            val inputText = "summarize: $limitedContent"

            val inputTokens = tokenizer.encode(inputText, MAX_INPUT_LENGTH)

            if (inputTokens.isEmpty()) {
                println("토큰화 결과가 비어있음")
                return@withContext "새로운 일기"
            }


            // 텐서 생성 및 추론
            println("모델 추론 시작...")
            val output = createTensorAndInfer(model, inputTokens)

            if (output == null) {
                println("모델 추론 실패 - output이 null")
                return@withContext "새로운 일기"
            }

            println("모델 추론 성공!")

            // 출력 처리 (메모리 효율적)
            val outputTokens = extractOutputTokensEfficient(output)
            println("출력 토큰 수: ${outputTokens.size}")
            println("출력 토큰들: ${outputTokens.take(20)}")

            if (outputTokens.isEmpty()) {
                println("출력 토큰이 비어있음")
                return@withContext "새로운 일기"
            }

            // 디토크나이징
            val generatedTitle = tokenizer.decode(outputTokens)
            println("디코딩된 제목: '$generatedTitle'")

            // 후처리
            val finalTitle = postProcessTitleEfficient(generatedTitle)
            println("최종 제목: '$finalTitle'")

            return@withContext finalTitle

        } catch (e: Exception) {
            e.printStackTrace()
            println("generateTitle 에러: ${e.message}")
            return@withContext "새로운 일기"
        } finally {
            // 매번 GC 실행으로 메모리 정리
            System.gc()
        }
    }

    private fun createTensorAndInfer(model: Module, inputTokens: List<Int>): IValue? {
        return try {
            println("Creating tensor for ${inputTokens.size} tokens")

            // 재사용 가능한 배열 사용
            val size = minOf(inputTokens.size, tempLongArray.size)
            for (i in 0 until size) {
                tempLongArray[i] = inputTokens[i].toLong()
            }

            // T5 모델은 input_ids와 attention_mask를 함께 받음
            val inputTensor = Tensor.fromBlob(
                tempLongArray.sliceArray(0 until size),
                longArrayOf(1, size.toLong())
            )

            // attention_mask 생성 (모든 토큰에 대해 1, padding 토큰에 대해서는 0)
            val attentionMask = LongArray(size) { 1L }
            val attentionTensor = Tensor.fromBlob(
                attentionMask,
                longArrayOf(1, size.toLong())
            )

            val result = model.forward(
                IValue.from(inputTensor),
                IValue.from(attentionTensor)
            )

            return result

        } catch (e: Exception) {
            println("T5 inference failed: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun extractOutputTokensEfficient(output: IValue): List<Int> {
        return try {
            when {
                output.isTensor -> {
                    val tensor = output.toTensor()
                    val shape = tensor.shape()

                    when {
                        tensor.dtype() == org.pytorch.DType.FLOAT32 -> {
                            val logits = tensor.dataAsFloatArray
                            if (shape.size >= 2) {
                                val vocabSize = shape[shape.size - 1].toInt()
                                val seqLength = minOf(logits.size / vocabSize, MAX_OUTPUT_LENGTH)

                                val result = mutableListOf<Int>()
                                for (i in 0 until seqLength) {
                                    val startIdx = i * vocabSize
                                    var maxIdx = 0
                                    var maxVal = logits[startIdx]

                                    for (j in 1 until vocabSize) {
                                        val idx = startIdx + j
                                        if (idx < logits.size && logits[idx] > maxVal) {
                                            maxVal = logits[idx]
                                            maxIdx = j
                                        }
                                    }
                                    result.add(maxIdx)
                                }
                                result
                            } else {
                                emptyList()
                            }
                        }
                        tensor.dtype() == org.pytorch.DType.INT64 -> {
                            tensor.dataAsLongArray.take(MAX_OUTPUT_LENGTH).map { it.toInt() }
                        }
                        tensor.dtype() == org.pytorch.DType.INT32 -> {
                            tensor.dataAsIntArray.take(MAX_OUTPUT_LENGTH).toList()
                        }
                        else -> emptyList()
                    }
                }
                output.isTuple -> {
                    val tuple = output.toTuple()
                    if (tuple.isNotEmpty() && tuple[0].isTensor) {
                        extractOutputTokensEfficient(tuple[0])
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            println("Output extraction failed: ${e.message}")
            emptyList()
        }
    }

    private fun postProcessTitleEfficient(title: String): String {
        var processedTitle = title.trim()

        // T5 특수 토큰 제거
        processedTitle = processedTitle
            .replace(Regex("<extra_id_\\d+>"), "") // <extra_id_0>, <extra_id_1> 등
            .replace(Regex("<[^>]+>"), "") // 기타 특수 토큰
            .replace("▁", " ") // SentencePiece 공백 복원
            .replace(Regex("\\s+"), " ") // 중복 공백 제거
            .trim()

        // 불필요한 prefix 제거
        processedTitle = processedTitle
            .replace(Regex("^(summarize:|summary:|title:|제목:|요약:)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // 반복되는 단어/문자 정리
        processedTitle = removeRepeatedPatterns(processedTitle)

        // 문장 부호 정리
        processedTitle = processedTitle
            .replace(Regex("[\"'`]+"), "")
            .replace(Regex("\\.$"), "")
            .trim()

        // 길이 제한
        val isKorean = processedTitle.any { it in '가'..'힣' }
        val maxLength = if (isKorean) 8 else 15

        if (processedTitle.length > maxLength) {
            // 단어 경계에서 자르기
            val words = processedTitle.split(" ")
            var truncated = ""
            for (word in words) {
                if ((truncated + " " + word).length <= maxLength) {
                    truncated = if (truncated.isEmpty()) word else "$truncated $word"
                } else {
                    break
                }
            }
            processedTitle = truncated.ifEmpty { processedTitle.take(maxLength) }
        }

        // 빈 제목 또는 너무 짧은 제목 처리
        if (processedTitle.length < 2 || processedTitle.all { !it.isLetterOrDigit() }) {
            return "새로운 일기"
        }

        return processedTitle
    }

    private fun removeRepeatedPatterns(text: String): String {
        var result = text

        // 같은 단어가 연속으로 반복되는 경우 제거
        result = result.replace(Regex("\\b(\\w+)\\s+\\1\\b"), "$1")

        // 같은 문자가 3번 이상 반복되는 경우 2번으로 줄이기
        result = result.replace(Regex("(.)\\1{2,}"), "$1$1")

        // 같은 패턴이 반복되는 경우 (예: "tion" -> "tiontion" -> "tiontiontion")
        for (len in 2..6) {
            val pattern = Regex("(.{$len})\\1+")
            result = pattern.replace(result) { matchResult ->
                matchResult.groupValues[1]
            }
        }

        return result
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

    // 디버깅용 메서드 추가
    suspend fun debugModelInfo() {
        initializeIfNeeded()
        println("=== 모델 디버그 정보 ===")
        println("모델 초기화 상태: $isInitialized")

        // 간단한 테스트 입력으로 모델 동작 확인
        val testResult = titleGenerator.generateTitle("테스트 일기 내용입니다.")
        println("테스트 생성 결과: '$testResult'")
    }
}