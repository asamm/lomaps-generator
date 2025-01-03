package com.asamm.locus.client


import com.asamm.locus.client.api.TilesetApi
import com.asamm.locus.client.api.TilesetIngestApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit

object MapTilerClient {

    private const val BASE_URL = "https://service.maptiler.com/v1/"

    private val SERVICE_TOKEN: String =
        "5fc611130dc44366a91c7d3a451c1fa4_e45828cb1fd56b5a70ec025bd4d49c0238f6cd6e340fbc5837a05491a37c70d1"

//    private val loggingInterceptor = HttpLoggingInterceptor().apply {
//        level = HttpLoggingInterceptor.Level.BODY
//    }

    val loggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        println("--> ${request.method} ${request.url}")
        println("Headers: ${request.headers}")

        val response = chain.proceed(request)

        // Peek up to 1 MB of the response body for logging
        val peekedBody = response.peekBody(1024 * 1024)
        println("<-- ${response.code} ${response.message}")
        println("Body: ${peekedBody.string()}")

        response // Return the original response to Retrofit
    }

    private val authInterceptor = AuthInterceptor(SERVICE_TOKEN)

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val json = Json { ignoreUnknownKeys = true }


    // TILESET API
    val tilesetApi: TilesetApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json; charset=UTF8".toMediaType()
                )
            )
            .build()
            .create(TilesetApi::class.java)
    }

    // TILESET INGEST API
    val tilesetIngestApi: TilesetIngestApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json; charset=UTF8".toMediaType()
                )
            )
            .build()
            .create(TilesetIngestApi::class.java)
    }
}

/**
 * Interceptor to add the Authorization header to all requests
 */
class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Token $token")
            .build()
        return chain.proceed(request)
    }
}