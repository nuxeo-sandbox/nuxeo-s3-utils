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
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;

/**
 * Deletes a file from S3, using S3Handler <code>handlerName</code> (if empty, uses the default handler).
 * <p>
 * If <code>bucket</code> is empty, uses the bucket set in the handler configuration
 * <p>
 * <b>About the {@value #RESULT_CONTEXT_VAR_NAME} context variable</b>: it is set to <code>"true"</code> when the
 * deletion was accepted by S3. It is never set to <code>"false"</code>: any error raises an exception, which aborts
 * the chain. Note also that S3 does not fail when the key does not exist, so a successful deletion does not mean the
 * object was there in the first place.
 *
 * @since 8.1
 */
@Operation(id = S3DeleteOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Delete", description = "Deletes a file from S3, using S3Handler <code>handlerName</code> (if empty, uses the default handler). Sets the s3UtilsDeletionResult context variable to \"true\" when the deletion was accepted by S3, any error raises an exception. S3 does not fail on a key that does not exist. If <code>bucket</code> is empty, uses the bucket set in the handler configuration")
public class S3DeleteOp {

    public static final String ID = "S3Utils.Delete";

    public static final String RESULT_CONTEXT_VAR_NAME = "s3UtilsDeletionResult";

    @Context
    protected OperationContext ctx;

    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;

    @Param(name = "bucket", required = false)
    protected String bucket;

    @Param(name = "key", required = true)
    protected String key;

    @OperationMethod
    public void run() throws NuxeoException, IOException {

        boolean result;

        if (StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }
        S3Handler s3Handler = S3Handler.getS3Handler(handlerName);
        // Never setBucket() here: the handler is a singleton shared by every caller
        result = s3Handler.deleteFile(bucket, key);

        ctx.put(RESULT_CONTEXT_VAR_NAME, result ? "true" : "false");
    }

}
