package com.androidtvopen.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object RemotePowerClient {

    suspend fun sendSleepCommand(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Uyku/Kapatma URL'si tanımlı değil. Ayarlar'dan ekleyebilirsiniz.")
            )
        }

        val normalizedUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(normalizedUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                useCaches = false
            }
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success("PC'ye uyku/kapatma komutu gönderildi (HTTP $code).")
            } else {
                Result.failure(Exception("PC yanıt kodu: HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("PC'ye ulaşılamadı: ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }
}
