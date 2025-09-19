package com.asamm.slack

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import com.asamm.store.LocusStoreEnv
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response


object SlackUtils {

    private const val TAG = "SlackUtils"

    // NOTE: consider moving webhook URL to an environment variable for security
    private const val WEBHOOK_URL = "https://hooks.slack.com/services/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"

    // Keep OkHttp timeout APIs compatible across OkHttp 4.x and 5.x
    val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { encodeDefaults = false }

    @Serializable
    private data class SlackMessage(val text: String)

    @Throws(IOException::class)
    fun sendMessage(message: String) {

        // Do not send message if DEV environment or running on local machine windows
        if (Utils.isLocalDEV() || AppConfig.config.locusStoreEnv == LocusStoreEnv.DEV) {
            Logger.w(TAG,"Slack message is not send because running in DEV environment or Locus Store DEV")
            Logger.w(TAG, "Slack message: $message")
            return
        }

        val payload = json.encodeToString(SlackMessage(message))
        val body = payload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            checkResponse(response)
        }
    }

    @Throws(IOException::class)
    private fun checkResponse(response: Response) {
        if (!response.isSuccessful) {
            val errorBody = try { response.body.string() } catch (_: Exception) { null }
            throw IOException("Unexpected code ${response.code} ${response.message}${if (errorBody.isNullOrBlank()) "" else ": $errorBody"}")
        }
    }
}