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
 *     thibaud
 */
package org.nuxeo.s3utils;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;

/**
 * @since 8.2
 */
public interface S3Handler {

    public void initialize(S3HandlerDescriptor desc) throws NuxeoException;

    public void cleanup();

    public void setBucket(String inBucket);

    public AmazonS3 getS3();

    public boolean sendFile(String inKey, File inFile) throws NuxeoException;

    public Blob downloadFile(String inKey, String inFileName) throws NuxeoException;

    public boolean deleteFile(String inKey) throws NuxeoException;

    public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType, String contentDisposition)
            throws NuxeoException;

    public String buildPresignedUrl(String inBucket, String inKey, int durationInSeconds, String contentType,
            String contentDisposition) throws NuxeoException;

    // This method should always check the key on S3, never looking in the cache (if any)
    public boolean existsKeyInS3(String inBucket, String inKey);

    // This method should always check the key on S3, never looking in the cache (if any)
    public boolean existsKeyInS3(String inKey);

    // This method should first check in the cache (CacheForKeyExists) if the key exists
    // If a cache is not used, it then just check in S3
    public boolean existsKey(String inKey);

    // This method should first check in the cache (CacheForKeyExists) if the key exists
    // If a cache is not used, it then just check in S3
    public boolean existsKey(String bucket, String inKey);

    public String getBucket();

    public int getSignedUrlDuration();

    public static S3Handler getS3Handler(String name) {

        S3HandlerService s3HandlerService = (S3HandlerService) Framework.getService(S3HandlerService.class);

        if (StringUtils.isBlank(name)) {
            name = Constants.DEFAULT_HANDLER_NAME;
        }

        return s3HandlerService.getS3Handler(name);

    }

    public static String buildDetailedMessageFromAWSException(Exception e) {

        String message = "";

        if (e instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException) e;
            message = "Caught an AmazonServiceException, which " + "means your request made it "
                    + "to Amazon S3, but was rejected with an error response" + " for some reason.";
            message += "\nError Message:    " + ase.getMessage();
            message += "\nHTTP Status Code: " + ase.getStatusCode();
            message += "\nAWS Error Code:   " + ase.getErrorCode();
            message += "\nError Type:       " + ase.getErrorType();
            message += "\nRequest ID:       " + ase.getRequestId();

        } else if (e instanceof AmazonClientException) {
            AmazonClientException ace = (AmazonClientException) e;
            message = "Caught an AmazonClientException, which " + "means the client encountered "
                    + "an internal error while trying to " + "communicate with S3, "
                    + "such as not being able to access the network.";
            message += "\nError Message: " + ace.getMessage();

        } else {
            message = e.getMessage();
        }

        return message;
    }

    public static boolean errorIsMissingKey(AmazonClientException e) {
        if (e instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException) e;
            return (ase.getStatusCode() == 404) || "NoSuchKey".equals(ase.getErrorCode())
                    || "Not Found".equals(e.getMessage());
        }
        return false;
    }

}
