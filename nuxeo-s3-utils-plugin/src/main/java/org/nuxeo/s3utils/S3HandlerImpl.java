/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thiabud Arguillere
 */
package org.nuxeo.s3utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

/**
 * Wrapper class around AmazonS3
 * 
 * @since 8.1
 */
public class S3HandlerImpl implements S3Handler {

    protected String name;

    protected String awsAccessKeyId;

    protected String awsSecretAccessKey;

    protected String currentBucket;

    protected int signedUrlDuration;

    protected AmazonS3 s3;

    protected CacheForKeyExists signedUrlCache;

    /**
     * Caller must call {¶{@link initialize} right after creating creating a new instance
     */
    public S3HandlerImpl() {
    }

    @Override
    public void initialize(S3HandlerDescriptor desc) throws NuxeoException {

        name = desc.getName();
        awsAccessKeyId = desc.getAwsKey();
        awsSecretAccessKey = desc.getAwsSecret();
        currentBucket = desc.getBucket();
        signedUrlDuration = desc.getTempSignedUrlDuration();

        setup();
    }

    protected void setup() {
        BasicAWSCredentials awsCredentialsProvider = new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey);
        s3 = new AmazonS3Client(awsCredentialsProvider);

        signedUrlCache = new CacheForKeyExists(this);
    }

    @Override
    public void cleanup() {

        if (signedUrlCache != null) {
            signedUrlCache.cleanup();
            signedUrlCache = null;
        }
    }

    /**
     * Allows to change the bucket (for the same account)
     * 
     * @param inBucket
     * @since 8.1
     */
    @Override
    public void setBucket(String inBucket) {
        currentBucket = inBucket;
        signedUrlCache.setBucket(inBucket);
    }

    /**
     * Return the {@link AmazonS3} object
     * 
     * @return the AmazonS3 object
     * @since 8.1
     */
    @Override
    public AmazonS3 getS3() {
        return s3;
    }

    /**
     * Sends the file to the bucket, returns <code>true</code> if the file was sent with no error.
     * 
     * @param inKey
     * @param inFile
     * @return true if all went ok
     * @throws NuxeoException
     * @since 8.1
     */
    @Override
    public boolean sendFile(String inKey, File inFile) throws NuxeoException {

        boolean ok = false;
        try {
            s3.putObject(new PutObjectRequest(currentBucket, inKey, inFile));
            ok = true;
        } catch (AmazonServiceException ase) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ase);
            throw new NuxeoException(message);

        } catch (AmazonClientException ace) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ace);
            throw new NuxeoException(message);
        }
        return ok;
    }

    /**
     * Download the file from S3
     * 
     * @param inKey
     * @param inFileName
     * @return the blob of the distant file
     * @throws IOException
     * @throws NuxeoException
     * @since 8.1
     */
    @Override
    public Blob downloadFile(String inKey, String inFileName) throws NuxeoException {

        ObjectMetadata metadata = null;

        Blob blob;
        try {
            blob = Blobs.createBlobWithExtension(".tmp");
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        try {
            GetObjectRequest gor = new GetObjectRequest(currentBucket, inKey);
            metadata = s3.getObject(gor, blob.getFile());

        } catch (AmazonServiceException ase) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ase);
            throw new NuxeoException(message);

        } catch (AmazonClientException ace) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ace);
            throw new NuxeoException(message);
        }

        if (metadata != null && blob.getFile().exists()) {
            if (StringUtils.isBlank(inFileName)) {
                int pos = inKey.lastIndexOf("/");
                if (pos > -1) {
                    inFileName = inKey.substring(pos + 1, inKey.length());
                } else {
                    inFileName = inKey;
                }
            }
            blob.setFilename(inFileName);
            blob.setMimeType(metadata.getContentType());
        }
        return blob;
    }

    /**
     * Delete the file in S3, returns <code>true</code> if the deletion went ok.
     * 
     * @param inKey
     * @return true if the file was deleted
     * @throws NuxeoException
     * @since 8.1
     */
    @Override
    public boolean deleteFile(String inKey) throws NuxeoException {

        boolean ok = false;
        try {
            s3.deleteObject(currentBucket, inKey);
            ok = true;
        } catch (AmazonServiceException ase) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ase);
            throw new NuxeoException(message);

        } catch (AmazonClientException ace) {
            String message = S3Handler.buildDetailedMessageFromAWSException(ace);
            throw new NuxeoException(message);
        }
        return ok;
    }

    /**
     * Builds a temporary signed URL for the object and returns it. If <code>inBucket</code> is empty, uses the
     * currentBucket, as set in the S3Handler of after a call to setBucket().
     * <p>
     * If <code>durationInSeconds</code> is <= 0, the default duration is used.
     * <p>
     * <code>contentType</code> and <code>contentDisposition</code> can be null or "", but it is recommended to set them
     * to make sure the is no ambiguity when the URL is used (a key without a file extension for example)
     * 
     * @param objectKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the URL to the file on S3
     * @throws IOException
     * @since 8.1
     */
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

        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + (durationInSeconds * 1000));

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(currentBucket, inKey, HttpMethod.GET);

        if (StringUtils.isNotBlank(contentType)) {
            request.addRequestParameter("response-content-type", contentType);
        }
        if (StringUtils.isNotBlank(contentDisposition)) {
            request.addRequestParameter("response-content-disposition", contentDisposition);
        }

        request.setExpiration(expiration);
        URL url = s3.generatePresignedUrl(request);

        try {
            URI uri = url.toURI();
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new NuxeoException(e);
        }

    }

    /**
     * Builds a temporary signed URL for the object and returns it. Uses the currentBucket, as set in the S3Handler of
     * after a call to setBucket().
     * <p>
     * If <code>durationInSeconds</code> is <= 0, the default duration is used.
     * <p>
     * <code>contentType</code> and <code>contentDisposition</code> can be null or "", but it is recommended to set them
     * to make sure the is no ambiguity when the URL is used (a key without a file extension for example)
     * 
     * @param objectKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the URL to the file on S3
     * @throws IOException
     * @since 8.1
     */
    @Override
    public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType, String contentDisposition)
            throws NuxeoException {

        return buildPresignedUrl(null, inKey, durationInSeconds, contentType, contentDisposition);

    }

    /**
     * Returns true if the key exists in the current bucket and never uses the CacheForKeyExists
     * <p>
     * Use the current bucket (default from the configuration, or the last one after calling setBucket()
     */
    @Override
    public boolean existsKeyInS3(String inKey) {

        boolean exists = false;

        try {
            @SuppressWarnings("unused")
            ObjectMetadata metadata = s3.getObjectMetadata(currentBucket, inKey);
            exists = true;
        } catch (AmazonClientException e) {
            if (!S3Handler.errorIsMissingKey(e)) {
                // Something else happened
                exists = true;
            }
        }

        return exists;
    }

    /**
     * Returns true if the key exists. Uses the cache. If the key is in the cache, returns true/false from the cache. If
     * it is not, the key is checked on S3, and the result is then cached.
     * <p>
     * Use the current bucket (default from the configuration, or the last one after calling setBucket()
     */
    @Override
    public boolean existsKey(String inKey) {

        return existsKey(null, inKey);

    }

    /**
     * Returns true if the key exists. Uses the cache. If the key is in the cache, returns true/false from the cache. If
     * it is not, the key is checked on S3, and the result is then cached.
     * <p>
     * If <code>bucket</code> is empty, uses the current one
     */
    @Override
    public boolean existsKey(String inBucket, String inKey) {

        if (StringUtils.isBlank(inBucket)) {
            inBucket = currentBucket;
        }

        return signedUrlCache.existsKey(inBucket, inKey);
    }

    public String getBucket() {
        return currentBucket;
    }

    public int getSignedUrlDuration() {
        return signedUrlDuration;
    }

}
