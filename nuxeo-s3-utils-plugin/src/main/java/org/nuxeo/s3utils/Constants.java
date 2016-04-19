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

/**
 * 
 * @since 8.1
 */
public class Constants {
    
    public static final String DEFAULT_HANDLER_NAME = "default";

    public static final String CONF_KEY_NAME_ACCESS_KEY = "nuxeo.aws.s3utils.keyid";

    public static final String CONF_KEY_NAME_SECRET_KEY = "nuxeo.aws.s3utils.secret";

    public static final String CONF_KEY_NAME_BUCKET = "nuxeo.aws.s3utils.bucket";

    public static final String CONF_KEY_NAME_DURATION = "nuxeo.aws.s3utils.duration";
    
    public static final int DEFAULT_SIGNED_URL_DIRATION = 3600;

}
