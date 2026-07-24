package com.tekutekunikki.mizunomi.data.remote

import com.tekutekunikki.mizunomi.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiClient {
    suspend fun generateCoachCard(
        systemInstruction: String,
        userInput: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini APIキーが設定されていません。local.properties を確認してください。"),
            )
        }

        runCatching {
            val connection = (URL(GenerateContentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TimeoutMillis
                readTimeout = TimeoutMillis
                doOutput = true
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(buildRequestBody(systemInstruction, userInput).toString())
                }

                val statusCode = connection.responseCode
                val responseText = if (statusCode in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }

                if (statusCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Gemini APIエラー（HTTP $statusCode）: ${extractErrorMessage(responseText)}")
                }

                extractText(responseText)
            } finally {
                connection.disconnect()
            }
        }.fold(
            onSuccess = { text -> Result.success(text) },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(
                        when (error) {
                            is IOException -> "Gemini APIとの通信に失敗しました。通信環境を確認してもう一度お試しください。"
                            else -> error.message ?: "Gemini APIとの通信に失敗しました。もう一度お試しください。"
                        },
                        error,
                    ),
                )
            },
        )
    }

    private fun buildRequestBody(
        systemInstruction: String,
        userInput: String,
    ): JSONObject =
        JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction)),
                ),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userInput)),
                    ),
                ),
            )

    private fun extractText(responseText: String): String {
        val text = JSONObject(responseText)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
            .trim()

        if (text.isBlank()) {
            throw IllegalStateException("Geminiから文章を取得できませんでした。安全性ブロックまたは空の応答の可能性があります。")
        }

        return text
    }

    private fun extractErrorMessage(responseText: String): String {
        if (responseText.isBlank()) {
            return "エラー内容を取得できませんでした。"
        }

        return runCatching {
            JSONObject(responseText)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: responseText
    }

    private companion object {
        private const val TimeoutMillis = 15_000
        private const val GenerateContentUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
    }
}
