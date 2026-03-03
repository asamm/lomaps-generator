package com.asamm.osmTools.server

import com.asamm.osmTools.config.AppConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.io.File
import java.net.URI

/**
 * S3 client that uploads a file to an S3  *
 * Credentials and connection settings are normally supplied via [AppConfig.config.onlineLoMapsConfig]
 *
 * @param accessKey   AWS access key ID
 * @param secretKey   AWS secret access key
 * @param region      AWS region (e.g. "eu-central-1")
 * @param bucketName  Target S3 bucket name
 * @param endpointUrl Optional custom endpoint for S3-compatible storage (e.g. MinIO).
 *                    Pass `null` to use the standard AWS endpoint.
 */
class S3Client(
    accessKey: String,
    secretKey: String,
    region: String,
    private val bucketName: String,
    endpointUrl: String? = null
) : AutoCloseable {

    companion object {
        /**
         * Creates an [S3Client] from the current [AppConfig] settings.
         * Credentials must have been loaded beforehand via [AppConfig.loadAwsCredentialsFromEnv].
         */
        fun fromAppConfig(): S3Client {
            val cfg = AppConfig.config.onlineLoMapsConfig
            return S3Client(
                accessKey = cfg.s3accessKey,
                secretKey = cfg.s3secretKey,
                region = cfg.s3region,
                bucketName = cfg.s3bucket,
                endpointUrl = cfg.s3endpoint.takeIf { it.isNotBlank() }
            )
        }
    }

    private val client: AwsS3Client = AwsS3Client.builder()
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        )
        .region(Region.of(region))
        .apply { if (endpointUrl != null) endpointOverride(URI.create(endpointUrl)) }
        .build()

    /**
     * Uploads [file] to S3 under the given [s3Key] (defaults to the file name).
     *
     * @throws software.amazon.awssdk.services.s3.model.S3Exception on S3 errors
     */
    fun uploadFile(file: File, s3Key: String = file.name) {
        require(file.exists() && file.isFile) { "Not a valid file: ${file.absolutePath}" }

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentLength(file.length())
            .build()

        println("S3Client: uploading '${file.name}' (${file.length()} bytes) → s3://$bucketName/$s3Key")
        val response = client.putObject(request, RequestBody.fromFile(file))
        println("S3Client: upload complete, ETag: ${response.eTag()}")
    }

    override fun close() {
        client.close()
    }
}

