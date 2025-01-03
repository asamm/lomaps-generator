package com.asamm.locus.client.api


import com.asamm.locus.client.model.Tileset
import com.asamm.locus.client.model.TilesetMetadataChange
import com.asamm.locus.client.model.TilesetPage
import retrofit2.Response
import retrofit2.http.*

interface TilesetApi {
    /**
     * Change tileset metadata
     *
     * Responses:
     *  - 200: Tileset
     *
     * @param documentId
     * @param tilesetMetadataChange  (optional)
     * @return [Tileset]
     */
    @POST("tiles/{document_id}/change_metadata")
    suspend fun tilesDocumentIdChangeMetadataPost(
        @Path("document_id") documentId: java.util.UUID,
        @Body tilesetMetadataChange: TilesetMetadataChange? = null,
    ): Response<Tileset>

    /**
     * Delete tileset
     *
     * Responses:
     *  - 200: Resource marked for deletion.
     *
     * @param documentId
     * @return [Unit]
     */
    @DELETE("tiles/{document_id}")
    suspend fun tilesDocumentIdDelete(@Path("document_id") documentId: java.util.UUID): Response<Unit>

    /**
     * Get tileset details
     *
     * Responses:
     *  - 200: Tileset
     *
     * @param documentId
     * @return [Tileset]
     */
    @GET("tiles/{document_id}")
    suspend fun tilesDocumentIdGet(@Path("document_id") documentId: java.util.UUID): Response<Tileset>

    /**
     * List tilesets belonging to your account
     *
     * Responses:
     *  - 200: Page of tilesets
     *
     * @param cursor Page cursor (optional)
     * @param limit Page limit (optional, default to 50)
     * @return [TilesetPage]
     */
    @GET("tiles")
    suspend fun tilesGet(
        @Query("cursor") cursor: kotlin.ByteArray? = null,
        @Query("limit") limit: kotlin.Int? = 50,
    ): Response<TilesetPage>

}
