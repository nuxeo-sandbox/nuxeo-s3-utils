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
 *   </s3Handler>
 *  </extension>
 * </pre></code>
 *
 * @since 8.2
 */
@XObject("s3Handler")
public class S3HandlerDescriptor {

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

    protected int signedUrlDuration = -1;

    protected int useExistsKeyCache = -1;

    public String getName() {
        return name;
    }

    public Class<?> getKlass() {
        return klass;
    }
    
    public String getRegion() {
        System.out.println("REGION 1: " + region);
        if(StringUtils.isBlank(region)) {
            region = Framework.getProperty(Constants.CONF_KEY_NAME_REGION);
            System.out.println("REGION 2: " + region);
            if(StringUtils.isBlank(region)) {
                region = Framework.getService(AWSConfigurationService.class).getAWSRegion();
            }
            System.out.println("REGION 3: " + region);
            if(StringUtils.isBlank(region)) {
                Framework.getProperty("nuxeo.aws.region");
            }
        }
        
        System.out.println("REGION FINAL: " + region);
        
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
            if (!tempSignedUrlDuration.isEmpty()) {
                try {
                    signedUrlDuration = (int) Long.parseLong(tempSignedUrlDuration);
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

}
