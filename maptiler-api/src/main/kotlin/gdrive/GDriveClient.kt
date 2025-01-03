package com.asamm.locus.gdrive


import com.asamm.locus.gdrive.api.GoogleDriveChunkedUploadApi
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.create
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern
import kotlin.math.pow

object GDriveClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    fun getApi(): GoogleDriveChunkedUploadApi {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/") // Placeholder
            .client(client)
            .build()
            .create(GoogleDriveChunkedUploadApi::class.java)
    }
}

// Function for uploading the file in chunks
suspend fun uploadToGoogleDrive(file: File, uploadUrl: String) {

    val api = GDriveClient.getApi()

    //minimal chunk size
    val minChunkSize = 262144 //
    val chunkSize = minChunkSize * 64 // 16MB
    val fileSize = file.length()
    var offset = 0L
    var retries = 0

    RandomAccessFile(file, "r").use { raf ->
        while (offset < fileSize) {
            val remaining = fileSize - offset
            val currentChunkSize = minOf(chunkSize.toLong(), remaining)
            val chunk = ByteArray(currentChunkSize.toInt())
            raf.seek(offset)
            raf.readFully(chunk)

            val contentRange = "bytes $offset-${offset + currentChunkSize - 1}/$fileSize"
            val requestBody = create("application/octet-stream".toMediaTypeOrNull(), chunk)

            try {
                val response = api.uploadChunk(uploadUrl, contentRange, requestBody)

                when {
                    response.isSuccessful && response.code() in 200..201 -> {
                        println("Upload complete!")
                        return
                    }

                    response.code() == 308 -> { // Resume incomplete upload
                        retries = 0
                        val rangeHeader = response.headers()["Range"]
                        val rangePattern = Pattern.compile("bytes=\\d+-(\\d+)")
                        val matcher = rangeHeader?.let { rangePattern.matcher(it) }
                        offset = if (matcher?.matches() == true) {
                            matcher.group(1)!!.toLong() + 1
                        } else {
                            offset + currentChunkSize
                        }
                    }

                    response.code() == 403 || response.code() >= 500 -> { // Retry for server errors
                        if (retries > 5) {
                            throw Exception("Upload failed after retries: ${response.errorBody()?.string()}")
                        }
                        delay(2.0.pow(retries).toLong() * 1000) // Exponential backoff
                        retries++
                    }

                    else -> {
                        throw Exception("Unexpected response: ${response.code()} - ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                println("Error during upload: ${e.message}")
                throw e
            }
        }
    }
}