/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.s3utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

/**
 * This class builds a Temporary Signed Url to access a file in a S3 bucket.
 * <p>
 * The configuration <b>must</b> (typically, in nuxeo.conf) contain at least these 2 keys...
 * <ul>
 * <li><code>nuxeo.aws.s3utils.keyid</code></li>
 * <li><code>nuxeo.aws.s3utils.secret</code></li>
 * </ul>
 * ...which are the key ID and secret key used to access AWS.
 * <p>
 * Also, the configuration <i>can</i> (not required) use the following parameters:
 * <ul>
 * <li><code>nuxeo.aws.s3utils.bucket</code>: A default bucket, to be used when no bucket is passed to the misc. APIs</li>
 * <li><code>nuxeo.aws.s3utils.duration</code>: the default duration, in seconds, for the url. To be used when no
 * duration (no parameters, or value less than 1) is passed to the APIs. If this configuration parameter is not set and
 * nno duration is provided when calling an API, then <code>S3TempSignedURLBuilder.DEFAULT_DURATION</code> is used.</li>
 * </ul>
 * 
 * @since 7.10
 */
public class S3TempSignedURLBuilder {

    public static final String CONF_KEY_NAME_ACCESS_KEY = "nuxeo.aws.s3utils.keyid";

    public static final String CONF_KEY_NAME_SECRET_KEY = "nuxeo.aws.s3utils.secret";

    public static final String CONF_KEY_NAME_BUCKET = "nuxeo.aws.s3utils.bucket";

    public static final String CONF_KEY_NAME_DURATION = "nuxeo.aws.s3utils.duration";

    public static final int DEFAULT_DURATION = 60 * 20; // 20mn

    protected static BasicAWSCredentials awsCredentialsProvider = null;

    protected static AmazonS3 s3;

    protected static String awsAccessKeyId = null;

    protected static String awsSecretAccessKey = null;

    protected static String awsBucket = null;

    protected static boolean setupDone = false;

    protected static int defaultDuration = -1;

    protected static final long MAX_IN_CACHED_KEYS = 500;

    protected static final long DURATION_IN_CACHE = 600000; // 10 minutes (in milliseconds)

    protected static LinkedHashMap<String, Boolean> cachedKeysAndExist = new LinkedHashMap<String, Boolean>();

    protected static LinkedHashMap<String, Long> cachedKeysAndSince = new LinkedHashMap<String, Long>();

    protected static final String LOCK = "S3TempSignedURLBuilder_lock";

    public S3TempSignedURLBuilder() {

        setup();
    }

    /*
     * We initialize the static variables, and we do it only once.
     */
    protected static void setup() {

        if (!setupDone) {
            synchronized (LOCK) {
                if (!setupDone) {
                    // Do it only once, even if an error occured (so we set it to true now, not at the end)
                    setupDone = true;

                    awsBucket = Framework.getProperty(CONF_KEY_NAME_BUCKET);
                    // Having no bucket name in the config is ok if a bucket is passed as argument to buld().

                    String durationStr = Framework.getProperty(CONF_KEY_NAME_DURATION);
                    if (StringUtils.isBlank(durationStr)) {
                        defaultDuration = DEFAULT_DURATION;
                    } else {
                        defaultDuration = Integer.parseInt(durationStr);
                    }

                    awsAccessKeyId = Framework.getProperty(CONF_KEY_NAME_ACCESS_KEY);
                    if (StringUtils.isBlank(awsAccessKeyId)) {
                        throw new NuxeoException("AWS Access Key (" + CONF_KEY_NAME_BUCKET
                                + ") is missing in the configuration.");
                    }

                    awsSecretAccessKey = Framework.getProperty(CONF_KEY_NAME_SECRET_KEY);
                    if (StringUtils.isBlank(awsAccessKeyId)) {
                        throw new NuxeoException("AWS Secret Key (" + CONF_KEY_NAME_SECRET_KEY
                                + ") is missing in the configuration.");
                    }

                    awsCredentialsProvider = new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey);
                    if (awsCredentialsProvider == null) {
                        throw new NuxeoException("AWS Access Key ID (" + CONF_KEY_NAME_ACCESS_KEY
                                + ") and/or Secret Access Key (" + CONF_KEY_NAME_SECRET_KEY
                                + ") are missing or invalid. Are they correctly set-up in the configuration?");

                    }

                    s3 = new AmazonS3Client(awsCredentialsProvider);
                }
            }
        }

    }

    /**
     * Return an url as string. This url is a temporary signed url giving access to the object for
     * <code>durationInSeconds</code> seconds. After this time, the object cannot be accessed anymore with this URL.
     * <p>
     * Some default values apply:
     * <p>
     * <ul>
     * <li>If <code>bucket</code> is empty (null, "", " ", ....), the bucket defined in the configuration is used.</li>
     * <li>If <code>durationInSeconds</code> is less than 1, the duration setup in the configuration (or
     * <code>S3TempSignedURLBuilder.DEFAULT_DURATION</code>) is used</li>
     * <li><code>contentType</code> and <code>contentDisposition</code> can be null or "", but it is recommended to set
     * them to make sure the is no ambiguity when the URL is used (a key without a file extension for example)</li>
     * </ul>
     * <p>
     * 
     * @param bucket
     * @param objectKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the temporary signed Url
     * @throws IOException
     * @since 7.10
     */
    public String build(String bucket, String objectKey, int durationInSeconds, String contentType,
            String contentDisposition) throws IOException {

        if (StringUtils.isBlank(bucket)) {
            bucket = awsBucket;
        }
        if (StringUtils.isBlank(bucket)) {
            throw new NuxeoException("No bucket provided, and configuration key " + CONF_KEY_NAME_BUCKET
                    + " is missing.");
        }

        Date expiration = new Date();
        if (durationInSeconds < 1) {
            durationInSeconds = defaultDuration;
        }
        expiration.setTime(expiration.getTime() + (durationInSeconds * 1000));

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.GET);

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
            throw new IOException(e);
        }

    }

    /**
     * Return an url as string. This url is a temporary signed url giving access to the object for
     * <code>durationInSeconds</code> seconds. After this time, the object cannot be accessed anymore with this URL.
     * <p>
     * Some default values apply:
     * <p>
     * <ul>
     * <li>The bucket used is the one defined in the configuration.</li>
     * <li>If <code>durationInSeconds</code> is less than 1, the duration setup in the configuration is used (or
     * <code>S3TempSignedURLBuilder.DEFAULT_DURATION</code>)</li>
     * <li><code>contentType</code> and <code>contentDisposition</code> can be null or "", but it is recommended to set
     * them to make sure the is no ambiguity when the URL is used (a key without a file extension for example)</li>
     * </ul>
     * <p>
     * 
     * @param objectKey
     * @param durationInSeconds
     * @param contentType
     * @param contentDisposition
     * @return the temporary signed Url
     * @throws IOException
     * @since 7.10
     */
    public String build(String objectKey, int durationInSeconds, String contentType, String contentDisposition)
            throws IOException {

        return build(awsBucket, objectKey, durationInSeconds, contentType, contentDisposition);

    }

    // ==========================================================================================
    // Handling "key exists", caching values to avoid multiple useless connections to S3
    // ==========================================================================================

    /*
     * Returns -1 if the key is not in the cache, 0 if it is in the cache and does not exist on S3, and 1 if it is in
     * the cache and exists on S3
     */
    protected static int existsKeyCheckInCache(String key) {

        int result = -1;

        if (StringUtils.isNotBlank(key)) {
            Boolean exists = cachedKeysAndExist.get(key);
            if (exists != null) {
                Long since = cachedKeysAndSince.get(key);
                long timeNow = System.currentTimeMillis();

                if ((timeNow - since) >= DURATION_IN_CACHE) {
                    cachedKeysAndExist.remove(key);
                    cachedKeysAndSince.remove(key);
                } else {
                    result = exists ? 1 : 0;
                }
            }
        }

        return result;

    }

    protected static void addToCachedKeys(String key, boolean exists) {

        if (StringUtils.isNotBlank(key)) {
            if (cachedKeysAndExist.size() >= MAX_IN_CACHED_KEYS) {
                // Delete the first 20
                String[] keys = new String[20];
                Iterator<Map.Entry<String, Boolean>> iterator = cachedKeysAndExist.entrySet().iterator();
                for (int i = 0; i < 20; ++i) {
                    keys[i] = iterator.next().getKey();
                }

                for (String oneKey : keys) {
                    cachedKeysAndExist.remove(oneKey);
                    cachedKeysAndSince.remove(oneKey);
                }
            }

            cachedKeysAndExist.put(key, exists);
            cachedKeysAndSince.put(key, System.currentTimeMillis());

        }
    }

    public static boolean existsKey(String objectKey) {

        return existsKey(null, objectKey);
    }

    public static boolean existsKey(String bucket, String objectKey) {

        boolean exists = false;

        if (StringUtils.isNotBlank(objectKey)) {

            // Called from a static method, we must make sure we initialize the misc. values (bucket, s3, ...)
            setup();

            if (StringUtils.isBlank(bucket)) {
                bucket = awsBucket;
            }
            
            String bucketAndKey = bucket + objectKey;
            int inCache = existsKeyCheckInCache(bucketAndKey);
            if (inCache != -1) {
                exists = inCache == 1;
            } else {
                try {
                    @SuppressWarnings("unused")
                    ObjectMetadata metadata = s3.getObjectMetadata(bucket, objectKey);
                    exists = true;
                } catch (AmazonClientException e) {
                    if (!errorIsMissingKey(e)) {
                        // Something else happened
                        exists = true;
                    }
                }

                addToCachedKeys(bucketAndKey, exists);
            }
        }

        return exists;
    }

    protected static boolean errorIsMissingKey(AmazonClientException e) {
        if (e instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException) e;
            return (ase.getStatusCode() == 404) || "NoSuchKey".equals(ase.getErrorCode())
                    || "Not Found".equals(e.getMessage());
        }
        return false;
    }

}
