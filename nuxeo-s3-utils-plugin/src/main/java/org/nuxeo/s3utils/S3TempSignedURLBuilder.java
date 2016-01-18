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

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

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

    private static AWSCredentialsProvider awsCredentialsProvider = null;

    protected AmazonS3 s3;

    protected static String awsAccessKeyId = null;

    protected static String awsSecretAccessKey = null;

    protected static String awsBucket = null;

    protected static int defaultDuration = -1;

    public S3TempSignedURLBuilder() {

        if (StringUtils.isBlank(awsBucket)) {
            awsBucket = Framework.getProperty(CONF_KEY_NAME_BUCKET);
            // Having no bucket name in the config is ok if a bucket is passed as argument to buld().
        }

        if (defaultDuration < 1) {
            String durationStr = Framework.getProperty(CONF_KEY_NAME_DURATION);
            if (StringUtils.isBlank(durationStr)) {
                defaultDuration = DEFAULT_DURATION;
            } else {
                defaultDuration = Integer.parseInt(durationStr);
            }
        }

        buildCredentiaProvider();
        if (awsCredentialsProvider == null) {
            throw new NuxeoException("AWS Access Key ID (" + CONF_KEY_NAME_ACCESS_KEY + ") and/or Secret Access Key ("
                    + CONF_KEY_NAME_SECRET_KEY
                    + ") are missing or invalid. Are they correctly set-up in the configuration?");

        }

        s3 = new AmazonS3Client(awsCredentialsProvider);
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

    protected void buildCredentiaProvider() {

        if (awsCredentialsProvider != null) {
            return;
        }

        awsAccessKeyId = Framework.getProperty(CONF_KEY_NAME_ACCESS_KEY);
        awsSecretAccessKey = Framework.getProperty(CONF_KEY_NAME_SECRET_KEY);

        if (StringUtils.isNotBlank(awsAccessKeyId) && StringUtils.isNotBlank(awsSecretAccessKey)) {
            awsCredentialsProvider = new SimpleAWSCredentialProvider(awsAccessKeyId, awsSecretAccessKey);
        }
    }

}
