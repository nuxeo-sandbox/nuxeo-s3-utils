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
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;

/**
 * Return a temporary signed url to the object in a the s3UtilsTempSignedUrl context variable.<br>
 * <br>
 * handler name, bucket and durationInSeconds can be empty, default values or values set in the configuration will apply.<br>
 * <br>
 * contentType and contentDisposition are optional but it is recommended to set them to make sure the is no ambiguity
 * when the URL is used (a key without a file extension for example)
 *
 * @since 9.10
 */
@Operation(id = S3TempSignedUrlOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Temp Signed URL", description = "Set the s3UtilsTempSignedUrl context variable to the temporary signed URL to the object. handler, bucket and duration are optional: If not passed, the default values apply (as set in the handler configuration)")
public class S3TempSignedUrlOp {

    public static final String ID = "S3Utils.TempSignedUrl";

    public static final String RESULT_CONTEXT_VAR_NAME = "s3UtilsTempSignedUrl";

    @Context
    protected OperationContext ctx;

    @Param(name = "key", required = true)
    protected String key;

    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;

    @Param(name = "bucket", required = false)
    protected String bucket;

    @Param(name = "durationInSeconds", required = false)
    protected Integer durationInSeconds;

    @Param(name = "contentType", required = false)
    protected String contentType;

    @Param(name = "contentDisposition", required = false)
    protected String contentDisposition;

    @OperationMethod
    public void run() throws NuxeoException, IOException {

        if (StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }

        S3Handler s3Handler = S3Handler.getS3Handler(handlerName);
        if (StringUtils.isNotBlank(bucket)) {
            s3Handler.setBucket(bucket);
        }

        if (durationInSeconds == null || durationInSeconds < 1) {
            durationInSeconds = s3Handler.getSignedUrlDuration();
        }

        String url = s3Handler.buildPresignedUrl(bucket, key, durationInSeconds, contentType, contentDisposition);

        ctx.put(RESULT_CONTEXT_VAR_NAME, url);
    }

}
