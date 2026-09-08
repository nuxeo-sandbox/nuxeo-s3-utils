/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.s3utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.aws.NuxeoAWSCredentialsProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

/**
 * Wrapper class around the AWS SDK v2 S3Client
 *
 * @since 8.1
 */
public class S3HandlerImpl implements S3Handler {

    protected static final Logger log = LogManager.getLogger(S3HandlerImpl.class);

    protected String name;

    protected String region;

    protected String currentBucket;

    protected int signedUrlDuration;

    protected boolean useCacheForExistsKey;

    protected S3Client s3;

    protected S3AsyncClient s3Async;

    protected S3TransferManager transferManager;

    protected AwsCredentialsProvider credentialsProvider;

    protected CacheForKeyExists keyExistsCache = null;

    protected long minimumUploadPartSize;

    long multipartUploadThreshold;

    /**
     * Caller must call {@link initialize} right after creating creating a new instance
     */
    public S3HandlerImpl() {
    }

    @Override
    public void initialize(S3HandlerDescriptor desc) throws NuxeoException {

        name = desc.getName();
        region = desc.getRegion();
        currentBucket = desc.getBucket();
        signedUrlDuration = desc.getTempSignedUrlDuration();
        useCacheForExistsKey = desc.useCacheForExistsKey();

        minimumUploadPartSize = desc.getMinimumUploadPartSize();
        multipartUploadThreshold = desc.getMultipartUploadThreshold();

        setup(desc);
    }

    protected void setup(S3HandlerDescriptor desc) {

        credentialsProvider = NuxeoAWSCredentialsProvider.getInstance();
        Region awsRegion = Region.of(region);

        s3 = S3Client.builder()
                     .region(awsRegion)
                     .credentialsProvider(credentialsProvider)
                     .httpClient(ApacheHttpClient.builder().build())
                     .build();

        /*
         * In the AWS SDK v1, the TransferManager accepted 0 for these two values, meaning "use the SDK defaults". The
         * v2 CRT client rejects 0, so we substitute the very same defaults the SDK used to apply.
         */
        long partSize = minimumUploadPartSize > 0 ? minimumUploadPartSize
                : S3HandlerDescriptor.MINIMUM_UPLOAD_PART_SIZE_DEFAULT;
        long threshold = multipartUploadThreshold > 0 ? multipartUploadThreshold
                : S3HandlerDescriptor.MULTIPART_UPLOAD_THRESHOLD_DEFAULT;

        s3Async = S3AsyncClient.crtBuilder()
                               .region(awsRegion)
                               .credentialsProvider(credentialsProvider)
                               .minimumPartSizeInBytes(partSize)
                               .thresholdInBytes(threshold)
                               .build();

        transferManager = S3TransferManager.builder().s3Client(s3Async).build();

        log.debug("S3Handler '{}' initialized on region '{}', bucket '{}', minimumUploadPartSize={}, "
                + "multipartUploadThreshold={}", name, region, currentBucket, partSize, threshold);

        if (useCacheForExistsKey) {
            keyExistsCache = new CacheForKeyExists(this);
        }
    }

    @Override
    public void cleanup() {

        if (keyExistsCache != null) {
            keyExistsCache.cleanup();
            keyExistsCache = null;
        }
        if (transferManager != null) {
            transferManager.close();
            transferManager = null;
        }
        if (s3Async != null) {
            s3Async.close();
            s3Async = null;
        }
        if (s3 != null) {
            s3.close();
            s3 = null;
        }
    }

    @Override
    public boolean sendFile(String inKey, File inFile) throws NuxeoException {

        try {
            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                                                                   .putObjectRequest(
                                                                           b -> b.bucket(currentBucket).key(inKey))
                                                                   .source(inFile)
                                                                   .build();
            FileUpload upload = transferManager.uploadFile(uploadFileRequest);
            // Be synchronous
            upload.completionFuture().join();
        } catch (CompletionException ce) {
            throw new NuxeoException(S3Handler.buildDetailedMessageFromAWSException(unwrap(ce)));
        } catch (SdkException se) {
            throw new NuxeoException(S3Handler.buildDetailedMessageFromAWSException(se));
        }

        return true;
    }

    @Override
    public Blob downloadFile(String inKey, File inDestFile) {

        HeadObjectResponse metadata;

        try {
            GetObjectRequest gor = GetObjectRequest.builder().bucket(currentBucket).key(inKey).build();
            DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
                                                                         .getObjectRequest(gor)
                                                                         .destination(inDestFile)
                                                                         .build();
            FileDownload download = transferManager.downloadFile(downloadFileRequest);
            /*
             * The GetObjectResponse and the HeadObjectResponse expose the same set of accessors we need here, but they
             * are unrelated types. We therefore re-read the metadata rather than mapping field by field.
             */
            download.completionFuture().join();
            metadata = getObjectMetadata(inKey);

        } catch (CompletionException ce) {
            throw new NuxeoException(S3Handler.buildDetailedMessageFromAWSException(unwrap(ce)));
        } catch (SdkException se) {
            throw new NuxeoException(S3Handler.buildDetailedMessageFromAWSException(se));
        }

        Blob blob = new FileBlob(inDestFile);
        blob.setDigest(cleanETag(metadata.eTag()));
        blob.setEncoding(metadata.contentEncoding());
        blob.setFilename(inDestFile.getName());
        blob.setMimeType(metadata.contentType());

        return blob;
    }

    @Override
    public SequenceInputStream getSequenceInputStream(String inKey, long pieceSize) throws IOException {

        S3ObjectSequentialStream seqStream = new S3ObjectSequentialStream(s3, currentBucket, inKey, pieceSize);

        return seqStream.getInputStream();

    }

    @Override
    public byte[] readBytes(String key, long start, long len) throws IOException {

        GetObjectRequest gor = GetObjectRequest.builder()
                                               .bucket(currentBucket)
                                               .key(key)
                                               .range("bytes=" + start + "-" + (start + len - 1))
                                               .build();
        try (InputStream stream = s3.getObject(gor)) {
            return stream.readAllBytes();
        }
    }

    @Override
    public Blob downloadFile(String inKey, String inFileName) throws NuxeoException {

        Blob blob;
        try {
            blob = Blobs.createBlobWithExtension(".tmp");
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        blob = downloadFile(inKey, blob.getFile());
        if (StringUtils.isBlank(inFileName)) {
            inFileName = FilenameUtils.getName(inKey);
        }
        blob.setFilename(inFileName);

        return blob;
    }

    @Override
    public boolean deleteFile(String inKey) throws NuxeoException {

        try {
            s3.deleteObject(b -> b.bucket(currentBucket).key(inKey));
        } catch (SdkException se) {
            throw new NuxeoException(S3Handler.buildDetailedMessageFromAWSException(se));
        }
        return true;
    }

    @Override
    public String buildPresignedUrl(String inBucket, String inKey, int durationInSeconds, String contentType,
            String contentDisposition) throws NuxeoException {

        if (StringUtils.isBlank(inBucket)) {
            inBucket = currentBucket;
        }
        if (StringUtils.isBlank(inBucket)) {
            throw new NuxeoException("No bucket provided");
        }

        if (durationInSeconds <= 0) {
            durationInSeconds = signedUrlDuration;
        }
        if (durationInSeconds <= 0) {
            throw new IllegalArgumentException("duration of " + durationInSeconds + " is invalid.");
        }

        String bucket = inBucket;
        Duration expiration = Duration.ofSeconds(durationInSeconds);

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                                                          .region(Region.of(region))
                                                          .credentialsProvider(credentialsProvider);

        try (S3Presigner presigner = presignerBuilder.build()) {
            java.net.URL url = presigner.presignGetObject(
                    r -> r.signatureDuration(expiration).getObjectRequest(gor -> {
                        gor.bucket(bucket).key(inKey);
                        if (StringUtils.isNotBlank(contentType)) {
                            gor.responseContentType(contentType);
                        }
                        if (StringUtils.isNotBlank(contentDisposition)) {
                            gor.responseContentDisposition(contentDisposition);
                        }
                    })).url();

            URI uri = url.toURI();
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new NuxeoException(e);
        }

    }

    @Override
    public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType, String contentDisposition)
            throws NuxeoException {

        return buildPresignedUrl(null, inKey, durationInSeconds, contentType, contentDisposition);

    }

    @Override
    public boolean existsKeyInS3(String inBucket, String inKey) {

        boolean exists = false;

        if (StringUtils.isBlank(inBucket)) {
            inBucket = currentBucket;
        }

        String bucket = inBucket;
        try {
            s3.headObject(b -> b.bucket(bucket).key(inKey));
            exists = true;
        } catch (SdkException e) {
            if (!S3Handler.errorIsMissingKey(e)) {
                // Something else happened
                exists = true;
            }
        }

        return exists;
    }

    @Override
    public boolean existsKeyInS3(String inKey) {

        return existsKeyInS3(null, inKey);
    }

    @Override
    public boolean existsKey(String inKey) {

        return existsKey(null, inKey);

    }

    @Override
    public boolean existsKey(String inBucket, String inKey) {

        if (StringUtils.isBlank(inBucket)) {
            inBucket = currentBucket;
        }

        if (keyExistsCache != null) {
            return keyExistsCache.existsKey(inBucket, inKey);
        } else {
            return existsKeyInS3(inBucket, inKey);
        }
    }

    @Override
    public HeadObjectResponse getObjectMetadata(String inKey) {

        try {
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(currentBucket).key(inKey).build();
            return s3.headObject(request);
        } catch (S3Exception e) {
            throw new NuxeoException(
                    "An error occurred while getting key %s in AWS bucket %s".formatted(inKey, currentBucket), e);
        }
    }

    @Override
    public JsonNode getObjectMetadataJson(String inKey) throws JsonProcessingException {

        HeadObjectResponse metadata = getObjectMetadata(inKey);

        /*
         * The AWS SDK v1 exposed ObjectMetadata#getRawMetadata(), a map keyed by HTTP header names. The v2
         * HeadObjectResponse has no such map, so we rebuild it explicitly. The keys below are the ones the v1 SDK
         * produced, so existing callers (automation chains reading "Content-Type", "Content-Length", "ETag", ...) keep
         * working.
         */
        Map<String, Object> mutableMap = new HashMap<>();
        putIfNotNull(mutableMap, "Content-Length", metadata.contentLength());
        putIfNotNull(mutableMap, "Content-Type", metadata.contentType());
        putIfNotNull(mutableMap, "ETag", cleanETag(metadata.eTag()));
        putIfNotNull(mutableMap, "Content-Encoding", metadata.contentEncoding());
        putIfNotNull(mutableMap, "Content-Disposition", metadata.contentDisposition());
        putIfNotNull(mutableMap, "Content-Language", metadata.contentLanguage());
        putIfNotNull(mutableMap, "Cache-Control", metadata.cacheControl());
        putIfNotNull(mutableMap, "Last-Modified",
                metadata.lastModified() == null ? null : metadata.lastModified().toString());
        putIfNotNull(mutableMap, "Expires", metadata.expiresString());
        putIfNotNull(mutableMap, "x-amz-version-id", metadata.versionId());
        putIfNotNull(mutableMap, "x-amz-storage-class",
                metadata.storageClass() == null ? null : metadata.storageClassAsString());
        putIfNotNull(mutableMap, "x-amz-server-side-encryption",
                metadata.serverSideEncryption() == null ? null : metadata.serverSideEncryptionAsString());

        mutableMap.put("bucketName", currentBucket);
        mutableMap.put("objectKey", inKey);
        mutableMap.put("userMetadata",
                metadata.metadata() == null ? new HashMap<String, String>() : metadata.metadata());

        // Convert Map to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.valueToTree(mutableMap);

        return json;
    }

    @Override
    public void setBucket(String inBucket) {
        currentBucket = inBucket;
        if (keyExistsCache != null) {
            keyExistsCache.setBucket(inBucket);
        }
    }

    @Override
    public S3Client getS3() {
        return s3;
    }

    @Override
    public String getBucket() {
        return currentBucket;
    }

    @Override
    public int getSignedUrlDuration() {
        return signedUrlDuration;
    }

    protected static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * S3 returns the ETag surrounded by double quotes. Strip them, as the v1 SDK used to do.
     *
     * @since 2025.1
     */
    protected static String cleanETag(String eTag) {
        if (eTag == null) {
            return null;
        }
        return StringUtils.strip(eTag, "\"");
    }

    /*
     * The transfer manager wraps every failure in a CompletionException. Get back to the real cause so that
     * buildDetailedMessageFromAWSException can produce a useful message.
     */
    protected static Exception unwrap(CompletionException ce) {
        Throwable cause = ce.getCause();
        if (cause instanceof Exception e) {
            return e;
        }
        return ce;
    }

}
