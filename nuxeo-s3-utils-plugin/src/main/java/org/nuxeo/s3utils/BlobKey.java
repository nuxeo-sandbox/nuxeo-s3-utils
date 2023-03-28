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


/**
 * Simple class to handle a blob key compatible with S3HandlerBlobProvider
 * 
 * Blob key is {providerId}:{bucketName}:{objectKey}
 * For example: "myProvider:demo-bucket-1:my cool image.jpg"
 * 
 * We do not throw an exception if, in the constructor, we don't find the blobProviderId
 * in the full key, it's up to the caller to handle error (or not) when getBucket() and/or
 * getObjectKey() return null.
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
    
    BlobKey(String blobProviderId, String fullKey) {
        
        this.blobProviderId = blobProviderId;
        parseFullKey(fullKey);
        
        if(bucket == null || objectKey == null) {
            //throw new NuxeoException("Invalid blob key ('" + fullKey + "') for provider " + blobProviderId);
        }
    }
    
    public boolean isValid() {
        return bucket!= null && objectKey != null;
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
    
    protected void parseFullKey(String fullKey) {
        
        int colon = fullKey.indexOf(':');
        if (colon >= 0 && fullKey.substring(0, colon).equals(blobProviderId)) {
            String tmp = fullKey.substring(colon + 1);
            colon = tmp.indexOf(':');
            if(colon >= 0) {
                bucket = tmp.substring(0, colon);
                objectKey = tmp.substring(colon + 1);
            }
        }
    }

}
