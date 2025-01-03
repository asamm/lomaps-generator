package com.asamm.locus.client.api


import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.asamm.locus.client.model.APIKey
import com.asamm.locus.client.model.APIKeyPage
import com.asamm.locus.client.model.APIKeySettings

interface APIKeysApi {
    /**
     * List API keys belonging to account
     * 
     * Responses:
     *  - 200: 
     *
     * @param cursor Page cursor (optional)
     * @param limit Page limit (optional, default to 50)
     * @return [APIKeyPage]
     */
    @GET("api_keys")
    suspend fun apiKeysGet(@Query("cursor") cursor: kotlin.ByteArray? = null, @Query("limit") limit: kotlin.Int? = 50): Response<APIKeyPage>

    /**
     * Update given key
     * 
     * Responses:
     *  - 200: 
     *
     * @param keyId Identifier of API Key.
     * @param apIKeySettings  (optional)
     * @return [APIKey]
     */
    @POST("api_keys/{key_id}/change_settings")
    suspend fun apiKeysKeyIdChangeSettingsPost(@Path("keyId") keyId: java.util.UUID, @Body apIKeySettings: APIKeySettings? = null): Response<APIKey>

    /**
     * Delete given key
     * 
     * Responses:
     *  - 200: Successfully deleted
     *
     * @param keyId Identifier of API Key.
     * @return [Unit]
     */
    @DELETE("api_keys/{key_id}")
    suspend fun apiKeysKeyIdDelete(@Path("keyId") keyId: java.util.UUID): Response<Unit>

    /**
     * Get key
     * 
     * Responses:
     *  - 200: 
     *
     * @param keyId Identifier of API Key.
     * @return [APIKey]
     */
    @GET("api_keys/{key_id}")
    suspend fun apiKeysKeyIdGet(@Path("keyId") keyId: java.util.UUID): Response<APIKey>

    /**
     * Create new key
     * 
     * Responses:
     *  - 200: 
     *
     * @param apIKeySettings  (optional)
     * @return [APIKey]
     */
    @POST("api_keys")
    suspend fun apiKeysPost(@Body apIKeySettings: APIKeySettings? = null): Response<APIKey>

}
