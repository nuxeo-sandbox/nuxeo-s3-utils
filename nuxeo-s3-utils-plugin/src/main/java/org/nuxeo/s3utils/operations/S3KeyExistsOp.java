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
 * Return a Boolean in a the s3UtilsKeyExists context variable, depending if the key exists in the bucket or
 * not.<br>
 * <br>
 * handler name and bucket, default values or values set in the configuration will apply.<br>
 * <br>
 * Byt default, the operation checks on S3 (does not use the cache). Ti use the cache (and if the S3Handler
 * configuration allows for it), set useCache parameter to true.
 *
 * @since 9.10
 */
@Operation(id = S3KeyExistsOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Key Exists", description = "Return a Boolean in a the s3UtilsTempSignedUrl context variable, depending if the key exists in the bucket or not. Set useCache to true to use the cache if the S3Handler configuraiton allows for it.")
public class S3KeyExistsOp {

    public static final String ID = "S3Utils.KeyExists";

    public static final String RESULT_CONTEXT_VAR_NAME = "s3UtilsKeyExists";

    @Context
    protected OperationContext ctx;

    @Param(name = "key", required = true)
    protected String key;

    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;

    @Param(name = "bucket", required = false)
    protected String bucket;

    @Param(name = "useCache", required = false)
    protected Boolean useCache = false;

    @OperationMethod
    public void run() throws NuxeoException, IOException {

        if (StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }

        S3Handler s3Handler = S3Handler.getS3Handler(handlerName);
        if (StringUtils.isNotBlank(bucket)) {
            s3Handler.setBucket(bucket);
        }

        boolean exists = false;
        if(useCache) {
            exists = s3Handler.existsKey(bucket, key);
        } else {
            exists = s3Handler.existsKeyInS3(bucket, key);
        }

        ctx.put(RESULT_CONTEXT_VAR_NAME, exists);
    }

}
