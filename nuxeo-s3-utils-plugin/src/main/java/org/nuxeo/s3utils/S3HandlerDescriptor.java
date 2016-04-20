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

import org.nuxeo.common.xmap.annotation.XNode;
//import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @since 8.1
 */
@XObject("s3Handler")
public class S3HandlerDescriptor {

    @XNode("name")
    protected String name = "";

    @XNode("class")
    protected Class<?> klass;

    @XNode("awsKey")
    protected String awsKey = "";

    @XNode("awsSecret")
    protected String awsSecret = "";

    @XNode("bucket")
    protected String bucket = "";

    @XNode("tempSignedUrlDuration")
    protected String tempSignedUrlDuration = "";
    
    protected int signedUrlDuration = -1;

    public String getName() {
        return name;
    }

    public Class<?> getKlass() {
        return klass;
    }

    public String getAwsKey() {
        return awsKey;
    }

    public String getAwsSecret() {
        return awsSecret;
    }

    public String getBucket() {
        return bucket;
    }

    public int getTempSignedUrlDuration() {
        if(signedUrlDuration < 0) {
            if(!tempSignedUrlDuration.isEmpty()) {
                try {
                    signedUrlDuration = (int) Long.parseLong(tempSignedUrlDuration);
                } catch (NumberFormatException e) {
                    signedUrlDuration = -1;
                }
            }
            if(signedUrlDuration < 0) {
                signedUrlDuration = Constants.DEFAULT_SIGNED_URL_DURATION;
            }
        }
        return signedUrlDuration;
    }
    
    

}
