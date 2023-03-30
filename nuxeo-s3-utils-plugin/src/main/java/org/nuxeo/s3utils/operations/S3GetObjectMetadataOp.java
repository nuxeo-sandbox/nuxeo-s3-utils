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
package org.nuxeo.s3utils.operations;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Gets obect metadata from S3, using S3Handler <code>handlerName</code> (if empty, uses the default handler).
 * <p>
 * If <code>bucket</code> is empty, uses the bucket set in the handler configuration
 * Returns a JsonBlob
 *
 * @since 2.0
 */
@Operation(id = S3GetObjectMetadataOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Get Object Metadata", description = ""
        + "Returns a JsonBlob with the object metadata, using S3Handler <code>handlerName</code> "
        + "(if empty, uses the default handler). Uses the bucket set in the handler configuration. "
        + "The returned JSON contains all the AWS metadata system fields, and the 'bucket', 'key' "
        + "and 'userMetadata' properties. The later is a Json object (can be empty) holding all the user metadata for the object. "
        + "If the object is  ot found (does not exist or current roel can't access it), returns an empty object {}")
public class S3GetObjectMetadataOp {

    public static final String ID = "S3Utils.GetObjectMetadata";

    @Context
    protected OperationContext ctx;

    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;

    @Param(name = "key", required = true)
    protected String key;

    @OperationMethod
    public Blob run() throws NuxeoException, IOException {

        if (StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }
        S3Handler s3Handler = S3Handler.getS3Handler(handlerName);
        JsonNode json = null;
        try {
            json = s3Handler.getObjectMetadataJson(key);
        } catch (NuxeoException e) {
            //Ignore
        }
        if(json == null) {
           return Blobs.createJSONBlob("{}");
        }

        return Blobs.createJSONBlob(json.toString());
    }

}
