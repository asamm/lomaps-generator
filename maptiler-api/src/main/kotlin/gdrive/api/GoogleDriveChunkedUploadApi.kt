package com.asamm.locus.gdrive.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Url

interface GoogleDriveChunkedUploadApi {
    @PUT
    suspend fun uploadChunk(
        @Url url: String,
        @Header("Content-Range") contentRange: String,
        @retrofit2.http.Body fileChunk: RequestBody
    ): Response<ResponseBody>
}