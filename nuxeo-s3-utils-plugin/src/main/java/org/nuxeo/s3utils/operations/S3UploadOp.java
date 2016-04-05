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
package org.nuxeo.s3utils.operations;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;

/**
 * Upload the blob to s3. Uses the bucket or the one set in the configuration. Returns the input unchanged.
 * <p>
 * The key is required, it gives the path where the blob must be stored.
 * 
 * @since 8.1
 */
@Operation(id = S3UploadOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Upload", description = "Uploads the blob to S3 using the bucket (default value read in the configuration) and the key. Returns the input unchanged. If the input is a document, the xpath can be passed to let the operation now where to read the blob from.")
public class S3UploadOp {

    public static final String ID = "S3Utils.Upload";

    @Param(name = "bucket", required = false)
    protected String bucket;

    @Param(name = "key", required = true)
    protected String key;

    @Param(name = "xpath", required = true, values = { "file:content" })
    protected String xpath;

    @OperationMethod
    public Blob run(Blob input) throws NuxeoException, IOException {

        if (input != null) {
            S3Handler s3Handler = new S3Handler();
            if (StringUtils.isNotBlank(bucket)) {
                s3Handler.setBucket(bucket);
            }
            /* boolean ignore = */s3Handler.sendFile(key, input.getFile());
        }

        return input;
    }

    @OperationMethod
    public DocumentModel run(DocumentModel input) throws NuxeoException, IOException {

        if (input != null) {

            if (StringUtils.isBlank(xpath)) {
                xpath = "file:content";
            }
            
            Blob b = (Blob) input.getPropertyValue(xpath);
            run(b);

        }
        return input;
    }

}
