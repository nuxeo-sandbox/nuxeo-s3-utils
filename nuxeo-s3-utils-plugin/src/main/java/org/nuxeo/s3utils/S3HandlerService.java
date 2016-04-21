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

/**
 * 
 * @since 8.2
 */
public interface S3HandlerService {

    /**
     * Return the S3Handler given its name. If not found in the XML contributions, returns null
     * 
     * @param name
     * @return the <S3Handler contributed, or null if not found
     * @since 8.2
     */
    public S3Handler getS3Handler(String name);
}
