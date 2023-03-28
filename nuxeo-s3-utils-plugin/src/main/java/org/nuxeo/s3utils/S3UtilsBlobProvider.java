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
 *     Michael Vachette (started this from his nuxeo-s3-simple-blobprovider plugin)
 */
package org.nuxeo.s3utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.file.FileCache;
import org.nuxeo.common.file.LRUFileCache;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.AbstractBlobProvider;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;

/**
 * A blob provider for handling S3 objects existing in other bucket than the one
 * used by the binary manager.
 * It is related to a S3Handler for getting region/bucket/AWS Credentials/etc.
 * We got inspiration from Michael Vachette's nuxeo-s3-simple-blobprovider
 * The filecache use S3 ETag as key
 * 
 * Some main points:
 *   - No write nor update
 *   - No URI => no direct download from a client
 * 
 * @since 2.1.1
 */
public class S3UtilsBlobProvider extends AbstractBlobProvider {

    protected static final Log log = LogFactory.getLog(S3UtilsBlobProvider.class);

    public static final String CACHE_SIZE_PROPERTY = "cachesize";

    public static final String CACHE_COUNT_PROPERTY = "cachecount";

    public static final String CACHE_MIN_AGE_PROPERTY = "cacheminage";

    public static final String S3_HANDLER_ATTACHED_PROPERTY = "s3handler";

    protected S3Handler s3Handler;

    protected File cachedir;

    public FileCache fileCache;

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {

        super.initialize(blobProviderId, properties);

        String s3HandlerAttached = properties.getOrDefault(S3_HANDLER_ATTACHED_PROPERTY,
                Constants.DEFAULT_HANDLER_NAME);
        s3Handler = S3Handler.getS3Handler(s3HandlerAttached);
        if (s3Handler == null) {
            throw new NuxeoException("Cannot initialize the S3UtilsBlobProvider because the related S3Handler named '"
                    + s3HandlerAttached + "' was not found.");
        }

        String cacheSizeStr = properties.getOrDefault(CACHE_SIZE_PROPERTY, "100 mb");
        String cacheCountStr = properties.getOrDefault(CACHE_COUNT_PROPERTY, "10000");
        String minAgeStr = properties.getOrDefault(CACHE_MIN_AGE_PROPERTY, "3600");

        initializeCache(SizeUtils.parseSizeInBytes(cacheSizeStr), Long.parseLong(cacheCountStr),
                Long.parseLong(minAgeStr));
    }

    @Override
    public void close() {
        fileCache.clear();
        if (cachedir != null) {
            try {
                FileUtils.deleteDirectory(cachedir);
            } catch (IOException e) {
                throw new NuxeoException(e);
            }
        }
    }

    @Override
    public Blob readBlob(BlobInfo blobInfo) throws IOException {

        if (blobInfo == null || blobInfo.key == null) {
            throw new IOException("Invalid blobinfo: " + blobInfo);
        }

        BlobKey blobKey = new BlobKey(blobProviderId, blobInfo.key);
        String bucket = blobKey.getBucket();
        String objectKey = blobKey.getObjectKey();
        if (bucket == null || objectKey == null) {
            throw new NuxeoException(String.format(
                    "Blob key (%s) does not match this provider (%s), so we have no bucket and no objectKey",
                    blobInfo.key, blobProviderId));
        }

        ObjectMetadata metadata;
        try {
            metadata = s3Handler.getS3().getObjectMetadata(bucket, objectKey);
        } catch (AmazonS3Exception e) {
            throw new NuxeoException(String.format("Could not get key %s in AWS bucket %s", objectKey, bucket), e);
        }

        blobInfo.length = metadata.getContentLength();
        blobInfo.digest = metadata.getContentMD5();
        blobInfo.encoding = metadata.getContentEncoding();
        blobInfo.mimeType = metadata.getContentType();
        int pos = objectKey.lastIndexOf("/");
        String fileName;
        if (pos > -1) {
            fileName = objectKey.substring(pos + 1, objectKey.length());
        } else {
            fileName = objectKey;
        }
        blobInfo.filename = fileName;

        return new SimpleManagedBlob(blobInfo);
    }

    @Override
    public InputStream getStream(ManagedBlob blob) throws IOException {
        File cachedFile = getFileFromCache(blob);
        return new FileInputStream(cachedFile);
    }

    @Override
    public String writeBlob(Blob blob) throws IOException {
        throw new UnsupportedOperationException("Write not supported");
    }

    @Override
    public boolean supportsUserUpdate() {
        return false;
    }

    /**
     * Initialize the cache.
     *
     * @param maxSize the maximum size of the cache (in bytes)
     * @param maxCount the maximum number of files in the cache
     * @param minAge the minimum age of a file in the cache to be eligible for removal (in seconds)
     * @since 5.9.2
     */
    protected void initializeCache(long maxSize, long maxCount, long minAge) throws IOException {
        cachedir = Framework.createTempFile("nxbincache.", "");
        cachedir.delete();
        cachedir.mkdir();
        fileCache = new LRUFileCache(cachedir, maxSize, maxCount, minAge);
    }

    /*
     * 
     */
    protected File getFileFromCache(ManagedBlob blob) throws IOException {

        BlobKey blobKey = new BlobKey(blobProviderId, blob.getKey());
        String bucket = blobKey.getBucket();
        String objectKey = blobKey.getObjectKey();
        if (bucket == null || objectKey == null) {
            throw new NuxeoException(String.format(
                    "Blob key (%s) does not match this provider (%s), so we have no bucket and no objectKey",
                    blob.getKey(), blobProviderId));
        }

        ObjectMetadata metadata;
        try {
            metadata = s3Handler.getS3().getObjectMetadata(bucket, objectKey);
        } catch (AmazonS3Exception e) {
            throw new NuxeoException(String.format("Could not get key %s in AWS bucket %s", objectKey, bucket), e);
        }

        String etag = metadata.getETag();
        File cachedFile = fileCache.getFile(etag);
        if (cachedFile == null) {
            File tmp = fileCache.getTempFile();
            /* Blob downloadedBlob = */s3Handler.downloadFile(objectKey, tmp);
            fileCache.putFile(etag, tmp);
            cachedFile = fileCache.getFile(etag);
        }

        return cachedFile;
    }
    
    public S3Handler getS3Handler() {
        return s3Handler;
    }

}
