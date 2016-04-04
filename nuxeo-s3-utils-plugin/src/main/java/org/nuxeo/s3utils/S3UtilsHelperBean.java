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
 *     Thiabud Arguillere
 */
package org.nuxeo.s3utils;

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;

/**
 * Bean helper containing misc. functions to be called from an .xml file, typically a widget template.
 * <p>
 * Here is an example, where the objectKey is stored in a document, in the "myschema:S3key" field:
 * <p>
 * 
 * <pre>
 * {@code
 * <div xmlns:c="http://java.sun.com/jstl/core" xmlns:nxl="http://nuxeo.org/nxforms/layout" xmlns:h="http://java.sun.com/jsf/html" xmlns:nxh="http://nuxeo.org/nxweb/html">
 *     <c:if test="#{widget.mode != 'edit'}">
 *         <h:outputLink value="#{s3UtilsHelper.getS3TempSignedUrl(field)}" target="_blank">#{widget.label}</h:outputLink>
 *     </c:if>  
 *     <c:if test="#{widget.mode == 'edit'}">
 *         <h:inputText value="#{field}" />
 *     </c:if> 
 * </div>
 * }
 * </pre>
 * 
 * @since 7.10
 */
@Name("s3UtilsHelper")
@Scope(ScopeType.EVENT)
public class S3UtilsHelperBean implements Serializable {

    private static final long serialVersionUID = -8550491926836699499L;

    /**
     * Return an S3 Temp Signed Url for the key, using the key id, secret key and bucket set in the configuration.
     * <p>
     * An empty key just returns an empty URL, not an error, so the bean can be used with values of fields of a document
     * when the field has not yet been set.
     * 
     * @param inKey
     * @return
     * @throws IOException
     * @since 7.10
     */
    public String getS3TempSignedUrl(String objectKey) throws IOException {

        String url = "";

        if (StringUtils.isNotBlank(objectKey)) {
            S3TempSignedURLBuilder builder = new S3TempSignedURLBuilder();
            url = builder.build(objectKey, 0, null, null);
        }

        return url;
    }

    /**
     * Return an S3 Temp Signed Url for this key in this bucket, using the key id and secret key set in the
     * configuration.
     * <p>
     * An empty key just returns an empty URL, not an error, so the bean can be used with values of fields of a document
     * when the field has not yet been set.
     * <p>
     * if the bucket is empty, the code uses the bucket set in the configuration.
     * 
     * @param inKey
     * @return
     * @throws IOException
     * @since 7.10
     */
    public String getS3TempSignedUrl(String bucket, String objectKey) throws IOException {

        String url = "";

        if (StringUtils.isNotBlank(objectKey)) {
            S3TempSignedURLBuilder builder = new S3TempSignedURLBuilder();
            url = builder.build(bucket, objectKey, 0, null, null);
        }

        return url;
    }

    /**
     * The full call. Return an S3 Temp Signed Url for this key, in this bucket, expiring in he given time, and setting
     * content-type and content-disposition (using the key id, secret key and bucket set in the configuration.)
     * <p>
     * Default values can apply, see {@link S3TempSignedURLBuilder} (default duration, default bucket, ...)
     * <p>
     * An empty key just returns an empty URL, not an error, so the bean can be used with values of fields of a document
     * when the field has not yet been set.
     * <p>
     * if the bucket is empty, the code uses the bucket set in the configuration.
     * 
     * @param inKey
     * @return
     * @throws IOException
     * @since 7.10
     */
    public String getS3TempSignedUrl(String bucket, String objectKey, int durationInSeconds, String contentType,
            String contentDisposition) throws IOException {

        String url = "";

        if (StringUtils.isNotBlank(objectKey)) {
            S3TempSignedURLBuilder builder = new S3TempSignedURLBuilder();
            url = builder.build(bucket, objectKey, durationInSeconds, contentDisposition, null);
        }

        return url;
    }

    /**
     * Returns true if the given key exists in S3 in the default (set in the configuration) bucket
     * 
     * @param objectKey
     * @return true if the key exist on S3 (in the default bucket)
     * @since 7.10
     */
    public boolean existsKey(String objectKey) {

        return S3TempSignedURLBuilder.existsKey(objectKey);
    }

    /**
     * Returns true if the given key exists in S3 in the given bucket (if the bucket is empty, we use the bucket set in
     * the configuration)
     * 
     * @param bucket
     * @param objectKey
     * @return true if the key exist on S3 in the given bucket
     * @since 7.10
     */
    public boolean existsKey(String bucket, String objectKey) {
        return S3TempSignedURLBuilder.existsKey(bucket, objectKey);
    }

}
