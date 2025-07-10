package com.asamm.locus

import com.asamm.locus.client.MapTilerClient
import com.asamm.locus.client.model.*
import com.asamm.locus.gdrive.GDriveClient
import com.asamm.locus.gdrive.uploadToGoogleDrive
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Utils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

class MapTilerUploader {

    fun uploadAndInitializeMapTiles(file: File){
        // Start the coroutine context
        return runBlocking {
            // Launch a coroutine to call the API method
            uploadMapTiles(file)
        }
    }


    private suspend fun uploadMapTiles(file: File) {

        val filename = Utils.getFileNamePart(file.toPath())
        val fileSize = file.length()
        val api = MapTilerClient.tilesetIngestApi
        val gdriveClient = GDriveClient.getApi()

        try {
            // Step 1: Get LoMaps Tileset
            var tilesetLoMap: Tileset? = getLoMapsTileset()

            // Step 3: Initiate ingestion
            val tilesetIngest = if (tilesetLoMap == null || tilesetLoMap.id == null) {
                println("LoMaps tileset does not exist. Creating a new one.")
                api.tilesIngestPost(TilesetIngestCreate(filename, fileSize)).body()
            } else {
                // Ingest the file into the existing tileset
                api.tilesDocumentIdIngestPost(tilesetLoMap.id as UUID, TilesetIngestCreate(filename, fileSize)).body()
            }

            // Step 3: Upload the file
            uploadToGoogleDrive(file, tilesetIngest?.uploadUrl ?: "")
            println("File uploaded to Gdrive")

            // Step 4: Start processing uploaded file
            val processingResponse = api.tilesIngestIngestIdProcessPost(tilesetIngest?.id as UUID).body()
            println("Processing started: ${processingResponse?.state}")

            // Step 5: Monitor status and wait until processing is completed
            val startTime = System.currentTimeMillis()
            val maxDuration = 6 * 60 * 60 * 1000 // 6 hours in milliseconds

            while (true) {
                val tilesetIngestStatus = api.tilesIngestIngestIdGet(tilesetIngest?.id as UUID).body()
                println("Current status: ${tilesetIngestStatus?.state} , progress: ${tilesetIngestStatus?.progress}")
                if (tilesetIngestStatus?.state == TilesetIngest.State.completed) break

                if (System.currentTimeMillis() - startTime > maxDuration) {
                    throw Exception("Processing of Tileset exceeded 6 hours.")
                }
                // pooling by DEV
                if (Utils.isLocalDEV()) {
                    Thread.sleep(5000) // Polling interval
                }
                else {
                    Thread.sleep(60000)
                }

            }

            // Step 6: update metedata
            api.tilesChangeMetadataPost(
                tilesetLoMap?.id as UUID,  TilesetMetadataChange(
                AppConfig.config.maptilerCloudConfig.tilesetTitleLm,
                AppConfig.config.maptilerCloudConfig.tilesetDescLm,
                AppConfig.config.maptilerCloudConfig.tilesetAttributionLm))

            println("Processing of uploaded tileset is complete")
        } catch (e: Exception) {
            println("MapTiler Uploader An error occurred: ${e.message}")
            throw e
        }
    }


    /**`
     * Get the UUID of the LoMaps tileset from MapTiler Cloud
     */
    private suspend fun getLoMapsTilesUUID(): UUID {
        val tilesets = MapTilerClient.tilesetApi.tilesGet(null, 100).body()
        val tileset = tilesets?.items?.find { it.title == AppConfig.config.maptilerCloudConfig.tilesetTitleLm }


        // if there is no tileset with the given title, raise exception
        if (tileset == null) {
            throw Exception("Tileset with title '${AppConfig.config.maptilerCloudConfig.tilesetTitleLm}' not found. Create the tileset first in MapTiler Cloud.")
        }
        if (tileset.id == null) {
            throw Exception("Tileset with title '${AppConfig.config.maptilerCloudConfig.tilesetTitleLm}' has no ID. Check the tileset in MapTiler Cloud.")
        }

        return tileset.id
    }

    private suspend fun getLoMapsTileset(): Tileset? {
        val tilesets = MapTilerClient.tilesetApi.tilesGet(null, 100).body()
        // Find the tileset with the given title or return null
        return tilesets?.items?.find { it.title == AppConfig.config.maptilerCloudConfig.tilesetTitleLm }
    }

}