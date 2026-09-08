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
import java.io.SequenceInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.file.FileCache;
import org.nuxeo.common.file.LRUFileCache;
import org.nuxeo.common.utils.ByteSize;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.blob.AbstractBlobProvider;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.platform.convert.ConvertHelper;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.mimetype.service.MimetypeRegistryService;
import org.nuxeo.runtime.api.Framework;

import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * A blob provider for handling S3 objects existing in other bucket than the one
 * used by the binary manager, adding some features (see noDefaultDownloadAbove for example, or the streaming from s3).
 * Thanks to Michael Vachette, we got inspiration from his nuxeo-s3-simple-blobprovider plugin.
 * <br>
 * It is related to a S3Handler for getting region/bucket/AWS Credentials/etc.
 * <br>
 * To handle several buckets, you must declare several S3Handlers, and then several Blob Providers, each using a
 * different S3Handler
 * <br>
 * Some main points:
 * - No write nor update
 * - No URI => no direct download from a client
 * - The filecache use S3 ETag as key
 * - Objects will be fetched only in the bucket handled by the related S3Handler
 * - See below explanation about "noDefaultDownloadAbove"
 * - It is possible to get a stream (a SequenceInputStream) from the distant object. This can be useful to avoid
 * downloading and storing the file when it is big and you don't actually need the file. See
 * S3UtilsBlobProvider#getInputStream
 * - It is also possible get a byte range directly from the S3 object (no need to download it all), see
 * S3UtilsBlobProvider#getBytes
 * <p>
 * </p>
 * <b>noDefaultDownloadAbove</b>:
 * - This property sets a threshold for downloading, so big big files are actually not downloaded, and, instead, the
 * plugin returns a light place holder blob. This allows for referencing these files in nuxeo without downloading them.
 * - The light place holder tries to match the object mimetype, but this could fail. It will work for text file, pdfs,
 * images, but for other types (videos, zip, Office, …), it will return simple text/plain blob.
 * - <b>IMPORTANT</b>: Of course there will be no automatic thumbnail, full text index, etc. for these files
 * - If its value is <= O, then files are always downloaded (this is the default value)
 * - You can still download the file by using the forceDownload() method
 * - So, for example, to avoid Nuxeo downloading files above 1GB:<br>
 * <property name="noDefaultDownloadAbove">1073741824</property>
 *
 * @since 2.1.1
 */
public class S3UtilsBlobProvider extends AbstractBlobProvider {

    protected static final Logger log = LogManager.getLogger(S3UtilsBlobProvider.class);

    public static final String NO_DEFAULT_DOWNLOAD_ABOVE_PROPERTY = "noDefaultDownloadAbove";

    public static final String CACHE_SIZE_PROPERTY = "cacheSize";

    public static final String CACHE_COUNT_PROPERTY = "cacheCount";

    public static final String CACHE_MIN_AGE_PROPERTY = "cacheMinAge";

    public static final String S3_HANDLER_ATTACHED_PROPERTY = "s3Handler";

    protected S3Handler s3Handler;

    protected File cachedir;

    public FileCache fileCache;

    protected long maxForDefaultDownload;

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

        initializeCache(ByteSize.parse(cacheSizeStr).bytes(), Long.parseLong(cacheCountStr),
                Long.parseLong(minAgeStr));

        String maxForDefaultDownloadStr = properties.getOrDefault(NO_DEFAULT_DOWNLOAD_ABOVE_PROPERTY, "0");
        maxForDefaultDownload = Long.parseLong(maxForDefaultDownloadStr);
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

        BlobKey blobKey = new BlobKey(blobProviderId, blobInfo.key, s3Handler.getBucket());
        String objectKey = blobKey.getObjectKey();

        BlobInfo fetchedInfo = buildBlobInfoForObject(objectKey);
        blobInfo.length = fetchedInfo.length;
        blobInfo.digest = fetchedInfo.digest;
        blobInfo.encoding = fetchedInfo.encoding;
        blobInfo.mimeType = fetchedInfo.mimeType;
        blobInfo.filename = fetchedInfo.filename;

        return new SimpleManagedBlob(blobInfo);
    }

    @Override
    public InputStream getStream(ManagedBlob blob) throws IOException {

        File cachedFile = getFileFromCache(blob);
        return new FileInputStream(cachedFile);
    }

    @Override
    public File getFile(ManagedBlob blob) {

        try {
            File cachedFile = getFileFromCache(blob);
            /*
             * Files in the cache are named after the ETag, callers expect the blob file name. We copy the cached file
             * to a dedicated temporary directory. We must never move it: moving would take the file away from the LRU
             * cache (every call would download the object again), would leave the renamed file behind, and two blobs
             * sharing the same file name would overwrite each other.
             */
            String fileName = FilenameUtils.getName(blob.getFilename());
            if (StringUtils.isBlank(fileName)) {
                fileName = cachedFile.getName();
            }
            File dir = Framework.createTempDirectory("s3utilsblob-").toFile();
            File result = new File(dir, fileName);
            Files.copy(cachedFile.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Let Nuxeo delete the copy once the caller has released it
            Framework.trackFile(result, result);

            return result;

        } catch (IOException e) {
            throw new NuxeoException("Error getting a file for blob key " + blob.getKey(), e);
        }
    }

    public SequenceInputStream getSequenceInputStream(ManagedBlob blob) throws IOException {

        BlobKey blobKey = new BlobKey(blobProviderId, blob.getKey(), s3Handler.getBucket());
        String objectKey = blobKey.getObjectKey();

        SequenceInputStream stream = s3Handler.getSequenceInputStream(objectKey, 0);

        return stream;
    }

    public byte[] readBytes(ManagedBlob blob, long start, long len) throws IOException {

        BlobKey blobKey = new BlobKey(blobProviderId, blob.getKey(), s3Handler.getBucket());
        String objectKey = blobKey.getObjectKey();

        return s3Handler.readBytes(objectKey, start, len);

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
        cachedir = Framework.createTempFile("s3utilsblobprovider.", "");
        cachedir.delete();
        cachedir.mkdir();
        fileCache = new LRUFileCache(cachedir, maxSize, maxCount, minAge);
    }

    /*
     *
     */
    protected File getFileFromCache(ManagedBlob blob) throws IOException {

        BlobKey blobKey = new BlobKey(blobProviderId, blob.getKey(), s3Handler.getBucket());
        String objectKey = blobKey.getObjectKey();

        HeadObjectResponse metadata = s3Handler.getObjectMetadata(objectKey);
        String etag = S3HandlerImpl.cleanETag(metadata.eTag());
        File cachedFile = fileCache.getFile(etag);
        if (cachedFile == null) {
            File tmp = fileCache.getTempFile();
            if (maxForDefaultDownload <= 0 || metadata.contentLength() <= maxForDefaultDownload) {
                /* Blob downloadedBlob = */s3Handler.downloadFile(objectKey, tmp);
            } else {
                buildFileWithObjectInfo(objectKey, metadata, tmp);
            }
            fileCache.putFile(etag, tmp);
            cachedFile = fileCache.getFile(etag);
        }

        return cachedFile;
    }

    public S3Handler getS3Handler() {
        return s3Handler;
    }

    /**
     * Creates a blob from the remote s3 object, the object is not downloaded.
     * <p>
     * <b>IMPORTANT</b>: the key of the returned blob is not {@code objectKey}, it is built with the provider's own key
     * scheme (see {@link BlobKey}). The object info (size, mime type, etc.) is read from S3.
     *
     * @param objectKey the object key on S3
     * @return a blob referencing the remote object
     * @throws IOException if the object info cannot be read
     * @since 2.1.1
     */
    public ManagedBlob createBlobFromObjectKey(String objectKey) throws IOException {

        BlobInfo info = buildBlobInfoForObject(objectKey);

        return new SimpleManagedBlob(info);
    }

    /*
     * Using the bucket defined in the s3 handler, of course
     */
    protected BlobInfo buildBlobInfoForObject(String objectKey) {

        BlobInfo info = new BlobInfo();

        HeadObjectResponse metadata = s3Handler.getObjectMetadata(objectKey);
        info.key = BlobKey.buildFullKey(blobProviderId, s3Handler.getBucket(), objectKey);
        info.length = metadata.contentLength();
        /*
         * The AWS SDK v2 has no equivalent of the v1 ObjectMetadata#getContentMD5(), which was in practice almost
         * always null on a HEAD response. We use the ETag instead, which is what the file cache already keys on.
         */
        info.digest = S3HandlerImpl.cleanETag(metadata.eTag());
        info.encoding = metadata.contentEncoding();
        info.mimeType = metadata.contentType();
        info.filename = FilenameUtils.getName(objectKey);

        return info;

    }

    protected File buildFileWithObjectInfo(String objectKey, HeadObjectResponse metadata, File toFile)
            throws IOException {

        String text = "This file is beyond the max. size for download\n\n";
        text += FilenameUtils.getName(objectKey) + "\n";
        text += FileUtils.byteCountToDisplaySize(metadata.contentLength()) + "\n";
        text += metadata.contentType() + "\n";

        MimetypeRegistryService mimeTypeService = (MimetypeRegistryService) Framework.getService(
                MimetypeRegistry.class);
        Optional<String> mimeTypeOpt = mimeTypeService.getNormalizedMimeType(metadata.contentType());
        String mimeType;
        if (mimeTypeOpt.isEmpty()) {
            mimeType = metadata.contentType();
        } else {
            mimeType = mimeTypeOpt.get();
        }
        ConvertHelper convertHelper = new ConvertHelper();

        Blob placeHolderBlob = Blobs.createBlob(text);
        // We need a file name or the converter may fail.
        placeHolderBlob.setFilename(FilenameUtils.getBaseName(objectKey) + "-noDownload.txt");
        Blob placeHolderBlobPdf;
        Blob converted;
        if (mimeType.startsWith("image/")) {
            placeHolderBlobPdf = convertHelper.convertBlob(placeHolderBlob, "application/pdf");
            // This does not work well... Too bad, it looked easy and simple
            // converted = convertHelper.convertBlob(placeHolderBlobPdf, mimeType);

            Map<String, Serializable> params = new HashMap<>();
            String fileName = FilenameUtils.getBaseName(objectKey) + "." + mimeType.split("/")[1];
            params.put("targetFileName", fileName);
            params.put("targetFilePath", fileName);

            ConversionService conversionService = Framework.getService(ConversionService.class);
            BlobHolder holder = conversionService.convert("pdf2image", new SimpleBlobHolder(placeHolderBlobPdf),
                    params);
            converted = holder.getBlob();
        } else {
            switch (mimeType) {
            case "application/pdf" -> converted = convertHelper.convertBlob(placeHolderBlob, "application/pdf");
            case "text/plain" -> converted = placeHolderBlob;
            default -> {
                /*
                 * A converter may not exist for this mime type, or may fail on our tiny place holder. This is not
                 * fatal: we degrade to a PDF, then to the plain text blob. We catch RuntimeException rather than
                 * Exception so that no checked exception, InterruptedException in particular, is ever swallowed here.
                 */
                try {
                    converted = convertHelper.convertBlob(placeHolderBlob, mimeType);
                } catch (RuntimeException e) {
                    log.warn("Failed to convert the place holder text/plain blob to {}, "
                            + "trying to convert it to PDF first, then convert this pdf", mimeType, e);
                    try {
                        placeHolderBlobPdf = convertHelper.convertBlob(placeHolderBlob, "application/pdf");
                        converted = convertHelper.convertBlob(placeHolderBlobPdf, mimeType);
                    } catch (RuntimeException e2) {
                        log.warn("Could not generate a blob for object {}, content type {}, normalized to {}: "
                                + "returning a simple string blob", objectKey, metadata.contentType(), mimeType, e2);
                        converted = placeHolderBlob;
                    }
                }
            }
            }
        }

        if (toFile != null) {
            converted.transferTo(toFile);
            return toFile;
        }
        return converted.getFile();

    }

    public long getMaxForDefaultDownload() {
        return maxForDefaultDownload;
    }

}
