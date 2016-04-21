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
 *     Thibaud Arguillere
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
 * Interface to be used by any S3Handler.
 * <p>
 * Some comments talk about the "current bucket". it is the bucket set in the XML contribution (S3HandlerDescriptor), or
 * the bucket explicitly set via <code>setBucket()</code>
 * 
 * @since 8.2
 */
public interface S3Handler {

    /**
     * Called after a new instance has been created.
     * <p>
     * <i>IMPORTANT</i>: Your S3Handler _must_ use this class, the S3HandlerService will call it.
     * 
     * @param desc
     * @throws NuxeoException
     * @since 8.2
     */
    public void initialize(S3HandlerDescriptor desc) throws NuxeoException;

    /**
     * Called when the S3Handler is removed. Notice that this should not happen very often. Most of the time, if not
     * every time, S3HandlerService creates and initializes the contributed handles at startup and releases them when
     * the server is shut down.
     * 
     * @since 8.2
     */
    public void cleanup();

    /**
     * The handler uses the bucket as set in the S3HandlerDescriptor at initialization time. BUt this can be changed
     * dynamically.
     * <p>
     * Notice that an easy way to handle different buckets on the same S3 instance is to contribute different S3Handler
     * in the XML. Thgis way, there is no need to swithc buckets, just use the correct handler.
     * 
     * @param inBucket
     * @since 8.2
     */
    public void setBucket(String inBucket);

    /**
     * @return the AmazonS3 instance created for this handler
     * @since 8.2
     */
    public AmazonS3 getS3();

    /**
     * Uploads inFile to S3, using the "Current bucket"
     * 
     * @param inKey
     * @param inFile
     * @return true if the file could be uploaded with no error
     * @throws NuxeoException
     * @since 8.2
     */
    public boolean sendFile(String inKey, File inFile) throws NuxeoException;

    /**
     * Downloads the file from S3 using the "current bucket".
     * <p>
     * <code>fileName</code> should be optional (not required by the interface)
     * 
     * @param inKey
     * @param inFileName
     * @return a Blob of the downloaded file
     * @throws NuxeoException
     * @since 8.2
     */
    public Blob downloadFile(String inKey, String inFileName) throws NuxeoException;

    /**
     * Deletes the file from S3 using the "current bucket", returns true if succesful
     * 
     * @param inKey
     * @return
     * @throws NuxeoException
     * @since 8.2
     */
    public boolean deleteFile(String inKey) throws NuxeoException;

    /**
     * Builds a temporary signed URL for the object and returns it.
     * <p>
     * Uses the "current bucket"
     * <p>
     * If <code>durationInSeconds</code> is <= 0, the duration set in the XML configuration is used (or the default
     * duration)
     * <p>
     * The interface allows <code>contentType</code> and <code>contentDisposition</code> to be empty. But it is
     * recommended to set them to make sure the is no ambiguity when the URL is used (a key without a file extension for
     * example)
     * 
     * @param objectKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the URL to the file on S3
     * @throws NuxeoException
     * @since 8.2
     */
    public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType, String contentDisposition)
            throws NuxeoException;

    /**
     * Builds a temporary signed URL for the object and returns it.
     * <p>
     * Uses <code>inBucket</code>. If it is empty, uses the "current bucket"
     * <p>
     * If <code>durationInSeconds</code> is <= 0, the duration set in the XML configuration is used (or the default
     * duration)
     * <p>
     * The interface allows <code>contentType</code> and <code>contentDisposition</code> to be empty. But it is
     * recommended to set them to make sure the is no ambiguity when the URL is used (a key without a file extension for
     * example)
     * 
     * @param inBucket
     * @param inKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the URL to the file on S3
     * @throws NuxeoException
     * @since 8.2
     */
    public String buildPresignedUrl(String inBucket, String inKey, int durationInSeconds, String contentType,
            String contentDisposition) throws NuxeoException;

    /**
     * Returns true if the key exists in the current bucket.
     * <p>
     * <b>IMPORTANT</b>: This method should <i>never</i> uses a CacheForKeyExists, and always requests the key on S3
     * 
     * @param inBucket
     * @param inKey
     * @return true if the key exists in the "current bucket"
     * @since 8.2
     */
    // This method should always check the key on S3, never looking in the cache (if any)
    public boolean existsKeyInS3(String inBucket, String inKey);

    /**
     * Returns true if the key exists in the bucket.
     * <p>
     * <b>IMPORTANT</b>: This method should <i>never</i> uses a CacheForKeyExists, and always requests the key on S3
     * 
     * @param inKey
     * @return true if the key exists in the bucket
     * @since 8.2
     */
    public boolean existsKeyInS3(String inKey);

    /**
     * Returns true if the key exists on S3 (using the "current bucket"), and should first check in the
     * CacheForExistsKey (if the configuraiton allows usage of the cache)
     * 
     * @param inKey
     * @return true is the key exists on S3. May use the cache
     * @since 8.2
     */
    public boolean existsKey(String inKey);

    /**
     * Returns true if the key exists on S3, in the bucket, and should first check in the CacheForExistsKey (if the
     * configuraiton allows usage of the cache)
     * 
     * @param bucket
     * @param inKey
     * @return
     * @since 8.2
     */
    public boolean existsKey(String bucket, String inKey);

    /**
     * Return the current bucket
     * 
     * @return the current bucket
     * @since 8.2
     */
    public String getBucket();

    /**
     * returns the default duration used to build temp. signed URLs
     * 
     * @return the default duration used to build temp. signed URLs
     * @since 8.2
     */
    public int getSignedUrlDuration();

    /**
     * Just a convenient method, saving one line of code (getting the service)
     * 
     * @param name
     * @return the S3Handler contributed with this name, null if not found
     * @since 8.2
     */
    public static S3Handler getS3Handler(String name) {

        S3HandlerService s3HandlerService = (S3HandlerService) Framework.getService(S3HandlerService.class);

        if (StringUtils.isBlank(name)) {
            name = Constants.DEFAULT_HANDLER_NAME;
        }

        return s3HandlerService.getS3Handler(name);

    }

    /**
     * Generic method used to build a message when an error is thrown by AWS
     * 
     * @param e
     * @return a string describing the error
     * @since 8.2
     */
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

    /**
     * Generic helper telling the caller if an error catched is a "MIsing Key on S3" error
     * 
     * @param e
     * @return true if the error is "MIssing Key" error
     * @since 8.2
     */
    public static boolean errorIsMissingKey(AmazonClientException e) {
        if (e instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException) e;
            return (ase.getStatusCode() == 404) || "NoSuchKey".equals(ase.getErrorCode())
                    || "Not Found".equals(e.getMessage());
        }
        return false;
    }

}
