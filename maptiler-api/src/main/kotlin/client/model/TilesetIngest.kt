/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.asamm.locus.client.model

import com.asamm.locus.client.model.Error

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializer

/**
 * 
 *
 * @param id 
 * @param documentId 
 * @param state 
 * @param filename 
 * @param propertySize size in bytes
 * @param errors 
 * @param progress 
 * @param uploadUrl URL for the uploaded resource
 */
@Serializable

data class TilesetIngest (

    @Serializable(with = UUIDSerializer::class) @SerialName(value = "id")
    val id: java.util.UUID? = null,

    @Serializable(with = UUIDSerializer::class) @SerialName(value = "document_id")
    val documentId: java.util.UUID? = null,

    @SerialName(value = "state")
    val state: TilesetIngest.State? = null,

    @SerialName(value = "filename")
    val filename: kotlin.String? = null,

    /* size in bytes */
    @SerialName(value = "size")
    val propertySize: kotlin.Long? = null,

    @SerialName(value = "errors")
    val errors: kotlin.collections.List<Error>? = null,

    @SerialName(value = "progress")
    val progress: kotlin.Double? = null,

    /* URL for the uploaded resource */
    @SerialName(value = "upload_url")
    val uploadUrl: kotlin.String? = null

) {

    /**
     * 
     *
     * Values: upload,processing,completed,canceled,failed
     */
    @Serializable
    enum class State(val value: kotlin.String) {
        @SerialName(value = "upload") upload("upload"),
        @SerialName(value = "processing") processing("processing"),
        @SerialName(value = "completed") completed("completed"),
        @SerialName(value = "canceled") canceled("canceled"),
        @SerialName(value = "failed") failed("failed");
    }
}

