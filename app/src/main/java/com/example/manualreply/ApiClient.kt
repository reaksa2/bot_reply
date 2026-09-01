package com.example.manualreply

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * All network calls run on background threads — callers must wrap
 * usage in Thread{} or similar and hop back to the UI thread to update views.
 *
 * Reads credentials from whichever Account is currently active
 * (see AccountManager) instead of fixed values, so switching accounts
 * in Settings immediately changes what every screen talks to.
 */
object ApiClient {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    class NoActiveAccountException : Exception("No account selected. Add one in Settings.")

    private fun requireActiveAccount(): Account {
        return AccountManager.getActiveAccount(appContext) ?: throw NoActiveAccountException()
    }

    // ---- Register a bot with the shared Worker backend ----
    // Requires the person to be signed in with Google (via Firebase Auth) —
    // the Worker verifies their identity before saving the bot registration.
    fun registerBot(botToken: String): RegisterResult {
        return try {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?: return RegisterResult(false, null, "Not signed in")

            val idTokenTask = user.getIdToken(true)
            val tokenResult = com.google.android.gms.tasks.Tasks.await(idTokenTask)
            val idToken = tokenResult.token ?: return RegisterResult(false, null, "Could not get auth token")

            val url = URL("${WorkerConfig.WORKER_BASE_URL}/register")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")

            val body = org.json.JSONObject().put("botToken", botToken).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code with no error body"
            }
            conn.disconnect()

            val json = org.json.JSONObject(responseText)
            if (json.optBoolean("ok")) {
                RegisterResult(true, json.getString("botId"), null)
            } else {
                RegisterResult(false, null, json.optString("error", "Registration failed (HTTP $code)"))
            }
        } catch (e: Exception) {
            RegisterResult(false, null, "Exception: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    data class RegisterResult(val success: Boolean, val botId: String?, val error: String?)

    data class UserProfile(val firstName: String, val lastName: String, val bio: String)

    // ---- Fetch a person's name/bio via Telegram's getChat API ----
    // Works for anyone the bot has previously exchanged messages with.
    fun fetchUserProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        val account = AccountManager.getActiveAccount(appContext) ?: return null

        return try {
            val url = "https://api.telegram.org/bot${account.botToken}/getChat?chat_id=$userId"
            val response = httpGet(url)
            val json = org.json.JSONObject(response)
            if (!json.optBoolean("ok")) return null

            val result = json.getJSONObject("result")
            UserProfile(
                firstName = result.optString("first_name", ""),
                lastName = result.optString("last_name", ""),
                bio = result.optString("bio", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---- Send a message AS THE BOT directly via Telegram Bot API ----
    fun sendTelegramReply(chatId: String, text: String): Boolean {
        val account = requireActiveAccount()
        val url = URL("https://api.telegram.org/bot${account.botToken}/sendMessage")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val params = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}" +
                "&text=${URLEncoder.encode(text, "UTF-8")}"

        conn.outputStream.use { it.write(params.toByteArray()) }

        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    // ---- Fetch a Telegram profile photo URL directly (no relay needed) ----
    fun fetchProfilePhotoUrl(userId: String): String? {
        if (userId.isBlank()) return null
        val account = AccountManager.getActiveAccount(appContext) ?: return null

        try {
            val photosUrl =
                "https://api.telegram.org/bot${account.botToken}/getUserProfilePhotos?user_id=$userId&limit=1"
            val photosResponse = httpGet(photosUrl)
            val photosJson = org.json.JSONObject(photosResponse)
            if (!photosJson.optBoolean("ok")) return null

            val result = photosJson.optJSONObject("result") ?: return null
            val photos = result.optJSONArray("photos") ?: return null
            if (photos.length() == 0) return null

            val sizes = photos.getJSONArray(0)
            val largest = sizes.getJSONObject(sizes.length() - 1)
            val fileId = largest.getString("file_id")

            return resolveFileUrl(fileId)
        } catch (e: Exception) {
            return null
        }
    }

    // ---- Resolve any Telegram file_id into a downloadable URL ----
    private fun resolveFileUrl(fileId: String): String? {
        val account = AccountManager.getActiveAccount(appContext) ?: return null
        try {
            val fileUrl = "https://api.telegram.org/bot${account.botToken}/getFile?file_id=$fileId"
            val fileResponse = httpGet(fileUrl)
            val fileJson = org.json.JSONObject(fileResponse)
            if (!fileJson.optBoolean("ok")) return null

            val filePath = fileJson.getJSONObject("result").getString("file_path")
            return "https://api.telegram.org/file/bot${account.botToken}/$filePath"
        } catch (e: Exception) {
            return null
        }
    }

    // ---- Send a photo AS THE BOT; returns the resolved media URL on success, null on failure ----
    fun sendTelegramPhoto(chatId: String, context: android.content.Context, uri: android.net.Uri): String? {
        val account = requireActiveAccount()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val responseBody = multipartUpload(
            "https://api.telegram.org/bot${account.botToken}/sendPhoto",
            chatId, "photo", "photo.jpg", "image/jpeg", bytes
        ) ?: return null

        return try {
            val json = org.json.JSONObject(responseBody)
            if (!json.optBoolean("ok")) return null
            val photos = json.getJSONObject("result").getJSONArray("photo")
            val largest = photos.getJSONObject(photos.length() - 1)
            resolveFileUrl(largest.getString("file_id"))
        } catch (e: Exception) {
            null
        }
    }

    // ---- Send a voice/audio recording AS THE BOT; returns the resolved media URL on success ----
    // Note: Telegram's dedicated "voice message" bubble requires OGG/Opus encoding.
    // This sends via sendAudio instead, which accepts common formats (e.g. m4a/AAC
    // from Android's MediaRecorder) and displays as a playable audio file — fully
    // functional, just shown as an audio attachment rather than the waveform bubble.
    fun sendTelegramAudio(chatId: String, file: java.io.File): String? {
        val account = requireActiveAccount()
        val bytes = file.readBytes()
        val responseBody = multipartUpload(
            "https://api.telegram.org/bot${account.botToken}/sendAudio",
            chatId, "audio", "voice.m4a", "audio/mp4", bytes
        ) ?: return null

        return try {
            val json = org.json.JSONObject(responseBody)
            if (!json.optBoolean("ok")) return null
            val fileId = json.getJSONObject("result").getJSONObject("audio").getString("file_id")
            resolveFileUrl(fileId)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Send a video AS THE BOT; returns the resolved media URL on success ----
    fun sendTelegramVideo(chatId: String, context: android.content.Context, uri: android.net.Uri): String? {
        val account = requireActiveAccount()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val responseBody = multipartUpload(
            "https://api.telegram.org/bot${account.botToken}/sendVideo",
            chatId, "video", "video.mp4", "video/mp4", bytes
        ) ?: return null

        return try {
            val json = org.json.JSONObject(responseBody)
            if (!json.optBoolean("ok")) return null
            val fileId = json.getJSONObject("result").getJSONObject("video").getString("file_id")
            resolveFileUrl(fileId)
        } catch (e: Exception) {
            null
        }
    }

    private fun multipartUpload(
        urlString: String,
        chatId: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): String? {
        val boundary = "----ManualReplyBoundary${System.currentTimeMillis()}"
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        conn.outputStream.use { out ->
            fun writeField(name: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                out.write("$value\r\n".toByteArray())
            }

            writeField("chat_id", chatId)

            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n".toByteArray())
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            out.write(fileBytes)
            out.write("\r\n".toByteArray())

            out.write("--$boundary--\r\n".toByteArray())
        }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        conn.disconnect()
        return body
    }

    private fun httpGet(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return text
    }
}
