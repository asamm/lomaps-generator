package com.asamm.locus.client.api

import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.asamm.locus.client.model.TilesetIngest
import com.asamm.locus.client.model.TilesetIngestCreate

interface TilesetIngestApi {
    /**
     * Ingest tileset container into existing tileset
     * 
     * Responses:
     *  - 200: Tileset ingest
     *
     * @param documentId 
     * @param tilesetIngestCreate  (optional)
     * @return [TilesetIngest]
     */
    @POST("tiles/{document_id}/ingest")
    suspend fun tilesDocumentIdIngestPost(@Path("document_id") documentId: java.util.UUID, @Body tilesetIngestCreate: TilesetIngestCreate? = null): Response<TilesetIngest>

    /**
     * Cancel tileset ingest
     * 
     * Responses:
     *  - 200: Tileset ingest
     *
     * @param ingestId 
     * @return [TilesetIngest]
     */
    @POST("tiles/ingest/{ingest_id}/cancel")
    suspend fun tilesIngestIngestIdCancelPost(@Path("ingest_id") ingestId: java.util.UUID): Response<TilesetIngest>

    /**
     * Get tileset ingest details
     * 
     * Responses:
     *  - 200: Tileset ingest
     *
     * @param ingestId 
     * @return [TilesetIngest]
     */
    @GET("tiles/ingest/{ingest_id}")
    suspend fun tilesIngestIngestIdGet(@Path("ingest_id") ingestId: java.util.UUID): Response<TilesetIngest>

    /**
     * Start tileset ingest processing
     * 
     * Responses:
     *  - 200: Tileset ingest
     *
     * @param ingestId 
     * @return [TilesetIngest]
     */
    @POST("tiles/ingest/{ingest_id}/process")
    suspend fun tilesIngestIngestIdProcessPost(@Path("ingest_id") ingestId: java.util.UUID): Response<TilesetIngest>

    /**
     * Ingest tileset container into a new tileset
     * 
     * Responses:
     *  - 200: Tileset ingest
     *
     * @param tilesetIngestCreate  (optional)
     * @return [TilesetIngest]
     */
    @POST("tiles/ingest")
    suspend fun tilesIngestPost(@Body tilesetIngestCreate: TilesetIngestCreate? = null): Response<TilesetIngest>

}
