package com.asamm.osmTools.utils

import com.asamm.osmTools.config.AppConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryMode
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.CopyRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.TransferListener
import java.io.File
import java.net.URI
import java.time.Duration

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

    // Standard (Netty) async client with native multipart support. CRT was previously used here but
    // misclassified DO Spaces' CompleteMultipartUpload responses as non-retryable
    // "Response code indicates internal server error", even when the object was uploaded successfully.
    private val asyncClient: S3AsyncClient = S3AsyncClient.builder()
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        )
        .region(Region.of(region))
        .endpointOverride(URI.create(endpointUrl))
        .forcePathStyle(true) // required for Ceph / DigitalOcean Spaces custom endpoints
        .multipartEnabled(true)
        .multipartConfiguration(
            MultipartConfiguration.builder()
                .minimumPartSizeInBytes(64 * 1024 * 1024L) // 64 MB per part
                .thresholdInBytes(64 * 1024 * 1024L)       // switch to multipart above 64 MB
                .build()
        )
        .httpClientBuilder(
            NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)
                .connectionTimeout(Duration.ofSeconds(50))
                // CompleteMultipartUpload stitch on DO Spaces for very large objects can take minutes.
                .readTimeout(Duration.ofMinutes(10))
                .writeTimeout(Duration.ofMinutes(10))
        )
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .retryStrategy(RetryMode.STANDARD)
                .apiCallAttemptTimeout(Duration.ofMinutes(30))
                .build()
        )
        .build()

    private val transferManager: S3TransferManager = S3TransferManager.builder()
        .s3Client(asyncClient)
        .build()

    /**
     * Uploads [file] to S3 under the given [s3Key] (defaults to the file name).
     *
     *
     * @throws software.amazon.awssdk.services.s3.model.S3Exception on S3 errors
     */
    @JvmOverloads
    fun uploadFile(file: File, s3Key: String = file.name, maxAttempts: Int = 1, retryDelayMs: Long = 30_000L) {

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
                        .addTransferListener(ProgressTransferListener.create("upload"))
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

    /**
     * Server-side copy within the configured bucket from [sourceKey] to [destKey].
     *
     * For objects larger than 5 GB [S3TransferManager] automatically switches to multipart copy
     * (`UploadPartCopy`); no bytes travel through the client. Progress events are reported via
     * [ProgressTransferListener] the same way as for uploads.
     *
     * @throws software.amazon.awssdk.services.s3.model.S3Exception on S3 errors
     */
    fun copyObject(sourceKey: String, destKey: String) {
        val copyObjectRequest = CopyObjectRequest.builder()
            .sourceBucket(bucketName)
            .sourceKey(sourceKey)
            .destinationBucket(bucketName)
            .destinationKey(destKey)
            .build()

        Logger.i(TAG, "S3Client: copying s3://$bucketName/$sourceKey → s3://$bucketName/$destKey")

        try {
            val copy = transferManager.copy(
                CopyRequest.builder()
                    .copyObjectRequest(copyObjectRequest)
                    .addTransferListener(ProgressTransferListener.create("copy"))
                    .build()
            )
            val result = copy.completionFuture().join()
            Logger.i(TAG, "S3Client: copy complete, ETag: ${result.response().copyObjectResult().eTag()}")
        } catch (e: java.util.concurrent.CompletionException) {
            val cause = e.cause
            if (cause is software.amazon.awssdk.services.s3.model.S3Exception) {
                Logger.e(TAG, "S3 error: statusCode=${cause.statusCode()}, " +
                        "code=${cause.awsErrorDetails()?.errorCode()}, " +
                        "message=${cause.awsErrorDetails()?.errorMessage()}")
            }
            Logger.e(TAG, "S3Client: copy failed: ${e.cause?.message ?: e.message}")
            throw e
        }
    }

    /**
     * Lists all object keys in the configured bucket whose names start with [prefix].
     * Handles pagination transparently — all pages are fetched and concatenated.
     */
    fun listObjects(prefix: String): List<String> {
        val keys = mutableListOf<String>()
        var continuationToken: String? = null
        do {
            val builder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
            if (continuationToken != null) {
                builder.continuationToken(continuationToken)
            }
            val response = asyncClient.listObjectsV2(builder.build()).join()
            response.contents()?.forEach { keys.add(it.key()) }
            continuationToken = if (response.isTruncated == true) response.nextContinuationToken() else null
        } while (continuationToken != null)
        return keys
    }

    /**
     * Deletes a single object identified by [key] from the configured bucket.
     */
    fun deleteObject(key: String) {
        val request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        asyncClient.deleteObject(request).join()
        Logger.i(TAG, "S3Client: deleted s3://$bucketName/$key")
    }

    override fun close() {
        transferManager.close()
        asyncClient.close()
    }

    // Custom TransferListener that logs progress for every 5% of the transfer completed.
    // [operation] is a human-readable label ("upload", "copy", ...) used in the log line.
    private class ProgressTransferListener(private val operation: String) : TransferListener {

        companion object {
            fun create(operation: String): ProgressTransferListener = ProgressTransferListener(operation)
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
                Logger.i(TAG, "S3 $operation progress: ${percent}%")
                lastLoggedPercent = percent
            }
        }
    }
}