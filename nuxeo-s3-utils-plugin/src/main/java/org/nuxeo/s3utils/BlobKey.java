/*
 * (C) Copyright 2023 Hyland (http://hyland.com/)  and others.
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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Simple class to handle a blob key compatible with S3HandlerBlobProvider
 * 
 * Blob key is {providerId}:{bucketName}:{objectKey}
 * For example: "myProvider:demo-bucket-1:my cool image.jpg"
 * 
 * Constructor throws an error if the providerId is not found, if the key is not
 * correctly formatted, etc.
 * 
 * @since 2.1.1
 */
public class BlobKey {
    
    protected String bucket;
    
    protected String objectKey;
    
    protected String blobProviderId;
    
    /**
     * Utility to create a key compatible with this class
     * 
     * @param providerId
     * @param bucket
     * @param objectKey
     * @return
     * @since 2.1.1
     */
    public static String buildFullKey(String providerId, String bucket, String objectKey) {
        return providerId + ":" + bucket + ":" + objectKey;
    }
    
    /**
     * Parses and checks the fullKey, throws errors if it is bad formatted.
     * 
     * @param blobProviderId
     * @param fullKey
     */
    public BlobKey(String blobProviderId, String fullKey) {
        
        if(StringUtils.isBlank(fullKey) || !fullKey.startsWith(blobProviderId)) {
            throw new NuxeoException(String.format("Blob key (%s) does not match this provider (%s).", fullKey, blobProviderId));
        }
        
        String[] parts = fullKey.split(":");
        if(parts.length != 3) {
            throw new NuxeoException(String.format("Blob key (%s) is mal formatted. Should be providerId:bucket:objectKey.", fullKey));
        }
        
        bucket = parts[1];
        objectKey = parts[2];
        if(bucket == null || objectKey == null) {
            throw new NuxeoException(String.format("Blob key (%s) is missing bucket and/or object key.", fullKey));
        }
    }
    
    /**
     * Parses and checks the fullKey, also checks the bucket referenced in the key is one expected.
     * 
     * Throws an error if the key is badly formatted or its referenced bucket is not the expected one.
     * 
     * @param blobProviderId
     * @param fullKey
     * @param expectedBucket
     */
    BlobKey(String blobProviderId, String fullKey, String expectedBucket) {
        
        this(blobProviderId, fullKey);
        if(!bucket.equals(expectedBucket)) {
            throw new NuxeoException(String.format("Bucket (%s) in the key (%s) does not match %s", bucket, fullKey, expectedBucket));
        }
    }
    
    public boolean isValid() {
        return bucket!= null && objectKey != null;
    }
    
    public String getProviderId() {
        return blobProviderId;
    }
    
    public String getBucket() {
        return bucket;
    }
    
    public String getObjectKey() {
        return objectKey;
    }
    
    public String buildFullKey(String objectKey) {
        return blobProviderId + ":" + bucket + ":" + objectKey;
    }

}
