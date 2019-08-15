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

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3HandlerServiceImpl;

/**
 * Uploads the blob to s3, using S3Handler <code>handlerName</code> (if empty, uses the default handler).
 * <p>
 * If <code>bucket</code> is empty, uses the bucket set in the handler configuration.
 * <p>
 * Returns the input unchanged.
 * <p>
 * The key is required, it gives the path where the blob must be stored.
 *
 * @since 8.1
 */
@Operation(id = S3UploadOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Upload", description = "Uploads the blob(s) to s3, using S3Handler <code>handlerName</code> (if empty, uses the default handler). If <code>bucket</code> is empty, uses the bucket set in the handler configuration. Returns the input unchanged. If the input is Document(s), uses the blob found in the xpath parameter")
public class S3UploadOp {

    public static final String ID = "S3Utils.Upload";

    @Context
    protected S3HandlerServiceImpl s3HandlerService;

    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;

    @Param(name = "bucket", required = false)
    protected String bucket;

    @Param(name = "key", required = true)
    protected String key;

    @Param(name = "xpath", required = false, values = { "file:content" })
    protected String xpath;

    protected S3Handler s3Handler;

    protected void setup() {

        if (StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }

        if (s3Handler == null) {
            s3Handler = S3Handler.getS3Handler(handlerName);
        }

        if (StringUtils.isNotBlank(bucket)) {
            s3Handler.setBucket(bucket);
        }

        if (StringUtils.isBlank(xpath)) {
            xpath = "file:content";
        }

    }

    @OperationMethod
    public Blob run(Blob blob) throws NuxeoException, IOException {

        if (blob != null) {

            setup();

            File f = blob.getFile();
            if (f != null) {
                @SuppressWarnings("unused")
                boolean ignore = s3Handler.sendFile(key, blob.getFile());
            }
        }

        return blob;
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws NuxeoException, IOException {

        if (doc != null) {

            setup();

            Blob b = (Blob) doc.getPropertyValue(xpath);
            run(b);

        }
        return doc;
    }

}
