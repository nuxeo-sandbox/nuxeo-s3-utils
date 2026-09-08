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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
//import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.aws.AWSConfigurationService;

import software.amazon.awssdk.regions.Region;

/**
 * Class handling the S3Handler contribution. Here is an example of XML contribution. Notice that we use an expression (
 * <code>${avalue:=}</code>) that allows to fill the value from the configuration (typically, nuxeo.conf) at startup.
 * Which is a recommended way to configure values.
 * <p>
 * <code><pre>
 * <extension target="org.nuxeo.s3utils.service" point="configuration">
 *   <s3Handler>
 *     <name>default</name>
 *     <class>org.nuxeo.s3utils.S3HandlerImpl</class>
 *     <region>${nuxeo.aws.s3utils.region:=}</region>
 *     <bucket>${nuxeo.aws.s3utils.bucket:=}</bucket>
 *     <tempSignedUrlDuration>${nuxeo.aws.s3utils.duration:=}</tempSignedUrlDuration>
 *     <useCacheForExistsKey>${nuxeo.aws.s3utils.use_cache_for_exists_key:=}</useCacheForExistsKey>
 *     
 *     <!-- No values (or 0) => Use the AWS SDK defaults -->
 *     <!-- 5MB (5242880) -->
 *     <minimumUploadPartSize>${nuxeo.aws.s3utils.minimumUploadPartSize:=}</minimumUploadPartSize>
 *     <!-- 16MB (16777216) -->
 *     <multipartUploadThreshold>${nuxeo.aws.s3utils.multipartUploadThreshold:=}</multipartUploadThreshold>
 *     
 *   </s3Handler>
 *  </extension>
 * </pre></code>
 *
 * @since 8.2
 */
@XObject("s3Handler")
public class S3HandlerDescriptor {

    /**
     * Default minimum upload part size, 5MB. Same value as the one the AWS SDK applies.
     *
     * @since 2025.1
     */
    public static final long MINIMUM_UPLOAD_PART_SIZE_DEFAULT = 5L * 1024 * 1024;

    /**
     * Default multipart upload threshold, 16MB. Same value as the one the AWS SDK applies.
     *
     * @since 2025.1
     */
    public static final long MULTIPART_UPLOAD_THRESHOLD_DEFAULT = 16L * 1024 * 1024;

    @XNode("name")
    protected String name = "";

    @XNode("class")
    protected Class<?> klass;

    @XNode("region")
    protected String region = "";

    @XNode("bucket")
    protected String bucket = "";

    @XNode("tempSignedUrlDuration")
    protected String tempSignedUrlDuration = "";

    @XNode("useCacheForExistsKey")
    protected String useCacheForExistsKey = "false";
    
    @XNode("minimumUploadPartSize")
    protected Long minimumUploadPartSize = 0L;
    
    @XNode("multipartUploadThreshold")
    protected Long multipartUploadThreshold = 0L;

    protected int signedUrlDuration = -1;

    protected int useExistsKeyCache = -1;

    public String getName() {
        return name;
    }

    public Class<?> getKlass() {
        return klass;
    }

    public String getRegion() {
        if (StringUtils.isBlank(region)) {
            region = Framework.getProperty(Constants.CONF_KEY_NAME_REGION);
        }
        if (StringUtils.isBlank(region)) {
            Region awsRegion = Framework.getService(AWSConfigurationService.class).getAwsRegion();
            region = awsRegion == null ? null : awsRegion.id();
        }
        if (StringUtils.isBlank(region)) {
            region = Framework.getProperty("nuxeo.aws.region");
        }

        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public boolean useCacheForExistsKey() {

        if (useExistsKeyCache < 0) {
            if (StringUtils.isNotBlank(useCacheForExistsKey) && "true".equals(useCacheForExistsKey.toLowerCase())) {
                useExistsKeyCache = 1;
            } else {
                useExistsKeyCache = 0;
            }
        }
        return useExistsKeyCache == 1;
    }

    public int getTempSignedUrlDuration() {
        if (signedUrlDuration < 0) {
            String duration = tempSignedUrlDuration == null ? "" : tempSignedUrlDuration.trim();
            if (!duration.isEmpty()) {
                try {
                    signedUrlDuration = (int) Long.parseLong(duration);
                } catch (NumberFormatException e) {
                    signedUrlDuration = -1;
                }
            }
            if (signedUrlDuration < 0) {
                signedUrlDuration = Constants.DEFAULT_SIGNED_URL_DURATION;
            }
        }
        return signedUrlDuration;
    }
    
    public long getMinimumUploadPartSize() {
        if (minimumUploadPartSize == null || minimumUploadPartSize <= 0) {
            minimumUploadPartSize = MINIMUM_UPLOAD_PART_SIZE_DEFAULT;
        }

        return minimumUploadPartSize;
    }

    public long getMultipartUploadThreshold() {
        if (multipartUploadThreshold == null || multipartUploadThreshold <= 0) {
            multipartUploadThreshold = MULTIPART_UPLOAD_THRESHOLD_DEFAULT;
        }

        return multipartUploadThreshold;
    }

}
