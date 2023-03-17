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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.nuxeo.runtime.aws.NuxeoAWSCredentialsProvider;

/**
 * Wrapper class around AmazonS3
 *
 * @since 8.1
 */
public class S3HandlerImpl implements S3Handler {
    
    protected static final Log log = LogFactory.getLog(S3HandlerImpl.class);

    protected String name;
    
    protected String region;

    protected String currentBucket;

    protected int signedUrlDuration;

    protected boolean useCacheForExistsKey;

    protected AmazonS3 s3;

    protected CacheForKeyExists keyExistsCache = null;

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

        setup(desc);
    }

    protected void setup(S3HandlerDescriptor desc) {
        
        AWSCredentialsProvider awsCredentialsProvider = NuxeoAWSCredentialsProvider.getInstance();
        s3 = AmazonS3ClientBuilder.standard()
                                  .withCredentials(awsCredentialsProvider)
                                  //.withClientConfiguration(HERE SOME CONFIG?)
                                  .withRegion(region)
                                  .build();
        
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
    }

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

    @Override
    public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType, String contentDisposition)
            throws NuxeoException {

        return buildPresignedUrl(null, inKey, durationInSeconds, contentType, contentDisposition);

    }

    @Override
    public boolean existsKeyInS3(String inBucket, String inKey) {

        boolean exists = false;

        if(StringUtils.isBlank(inBucket)) {
            inBucket = currentBucket;
        }

        try {
            @SuppressWarnings("unused")
            ObjectMetadata metadata = s3.getObjectMetadata(inBucket, inKey);
            exists = true;
        } catch (AmazonClientException e) {
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
    public JsonNode getObjectMetadata(String inKey) throws JsonProcessingException {
        
        ObjectMetadata metadata;
        try {
            metadata = s3.getObjectMetadata(currentBucket, inKey);
        } catch(AmazonS3Exception e) {
            if(e.getErrorMessage().toLowerCase().equals("not found")) {
                return null;
            }
            throw e;
        }
        
        Map<String, Object> metadataMap = metadata.getRawMetadata();
        Map<String, Object> mutableMap = new HashMap<String, Object>(metadataMap);
        mutableMap.put("bucketName",currentBucket );
        mutableMap.put("objectKey", inKey);
        
        Map<String, String> userMetadata = metadata.getUserMetadata();
        mutableMap.put("userMetadata", userMetadata);

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
    public AmazonS3 getS3() {
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

}
