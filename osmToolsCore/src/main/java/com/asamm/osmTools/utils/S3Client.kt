package com.asamm.osmTools.utils

import com.asamm.osmTools.config.AppConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.TransferListener
import java.io.File
import java.net.URI

/**
 * S3 client that uploads a file to an S3 bucket using multipart upload via [S3TransferManager].
 * Credentials and connection settings are supplied via [AppConfig].
 *
 * @param accessKey   AWS access key ID
 * @param secretKey   AWS secret access key
 * @param region      AWS region (e.g. "eu-central-1")
 * @param bucketName  Target S3 bucket name
 * @param endpointUrl Optional custom endpoint for S3-compatible storage (e.g. DigitalOcean Spaces).
 *                    Pass `null` to use the standard AWS endpoint.
 */
class S3Client(
    accessKey: String,
    secretKey: String,
    region: String,
    private val bucketName: String,
    endpointUrl: String
) : AutoCloseable {

    companion object {

        val TAG: String = S3Client::class.java.simpleName

        /**
         * Creates an [S3Client] from the current [AppConfig] settings.
         * Credentials must have been loaded beforehand via `ConfigUtils.loadAwsCredentialsFromEnv`.
         */
        fun fromAppConfig(): S3Client {
            val cfg = AppConfig.config.onlineLoMapsConfig
            return S3Client(
                accessKey = cfg.s3accessKey,
                secretKey = cfg.s3secretKey,
                region = cfg.s3region,
                bucketName = cfg.s3bucket,
                endpointUrl = cfg.s3endpoint
            )
        }
    }

    private val asyncClient: S3AsyncClient = S3AsyncClient.crtBuilder()
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        )
        .region(Region.of(region))
        .endpointOverride(URI.create(endpointUrl))
        .forcePathStyle(true)                       // required for Ceph / DigitalOcean Spaces custom endpoints
        .minimumPartSizeInBytes(64 * 1024 * 1024L) // 64 MB per part
        .build()

    private val transferManager: S3TransferManager = S3TransferManager.builder()
        .s3Client(asyncClient)
        .build()

    /**
     * Uploads [file] to S3 under the given [s3Key] (defaults to the file name).
     * Retries up to [maxAttempts] times on transient failures, waiting [retryDelayMs] ms between attempts.
     *
     * @throws software.amazon.awssdk.services.s3.model.S3Exception on S3 errors
     */
    fun uploadFile(file: File, s3Key: String = file.name, maxAttempts: Int = 2, retryDelayMs: Long = 30_000L) {

        require(file.exists() && file.isFile) { "Not a valid file: ${file.absolutePath}" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build()

        Logger.i(TAG, "S3Client: uploading '${file.name}' (${file.length()} bytes) → s3://$bucketName/$s3Key")
        Logger.i(TAG, "S3Client: " +
                "endpoint=${ asyncClient.serviceClientConfiguration().endpointOverride().orElse(null) }, " +
                "region=${asyncClient.serviceClientConfiguration().region()}")

        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                Logger.i(TAG, "S3Client: retrying upload (attempt $attempt/$maxAttempts) after ${retryDelayMs}ms delay…")
                Thread.sleep(retryDelayMs)
            }
            try {
                val upload = transferManager.uploadFile(
                    UploadFileRequest.builder()
                        .putObjectRequest(request)
                        .source(file.toPath())
                        .addTransferListener(ProgressTransferListener.create())
                        .build()
                )

                val result = upload.completionFuture().join()
                Logger.i(TAG, "S3Client: upload complete, ETag: ${result.response().eTag()}")
                return
            } catch (e: java.util.concurrent.CompletionException) {
                val cause = e.cause
                if (cause is software.amazon.awssdk.services.s3.model.S3Exception) {
                    Logger.e(TAG, "S3 error: statusCode=${cause.statusCode()}, " +
                            "code=${cause.awsErrorDetails()?.errorCode()}, " +
                            "message=${cause.awsErrorDetails()?.errorMessage()}")
                }
                Logger.e(TAG, "S3Client: upload attempt $attempt/$maxAttempts failed: ${e.cause?.message ?: e.message}")
                lastException = e
            }
        }
        throw lastException!!
    }

    override fun close() {
        transferManager.close()
        asyncClient.close()
    }

    // Custom TransferListener that logs progress for every 5% of the upload completed.
    private class ProgressTransferListener : TransferListener {

        companion object {
            fun create(): ProgressTransferListener = ProgressTransferListener()
        }

        private var lastLoggedPercent: Int = -1

        override fun bytesTransferred(context: TransferListener.Context.BytesTransferred?) {
            val ratio = context?.progressSnapshot()?.ratioTransferred()?.asDouble
            if (ratio == null || ratio.isNaN()) {
                return
            }

            val percent = (ratio * 100).toInt()

            if (percent == lastLoggedPercent) {
                return
            }

            val shouldLog = when {
                percent <= 10 -> true                     // log every percent from 0..10
                percent == 100 -> true                    // always log completion
                percent > 10 && percent % 5 == 0 -> true  // above 10, log every 5%
                else -> false
            }

            if (shouldLog) {
                Logger.i(TAG, "S3 upload progress: ${percent}%")
                lastLoggedPercent = percent
            }
        }
    }
}