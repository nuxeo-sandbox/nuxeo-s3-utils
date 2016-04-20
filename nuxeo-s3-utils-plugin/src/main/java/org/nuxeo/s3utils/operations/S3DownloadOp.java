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
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3HandlerServiceImpl;

/**
 * 
 * @since 8.1
 */
@Operation(id = S3DownloadOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Download", description = "")
public class S3DownloadOp {
    
    public static final String ID = "S3Utils.Download";

    @Context
    protected S3HandlerServiceImpl s3HandlerService;
    
    @Param(name = "handlerName", required = false, values = { org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME })
    protected String handlerName;
    
    @Param(name = "bucket", required = false)
    protected String bucket;
    
    @Param(name = "key", required = true)
    protected String key;
    
    @OperationMethod
    public Blob run() throws NuxeoException, IOException {
        
        Blob result = null;
        
        if(StringUtils.isBlank(handlerName)) {
            handlerName = org.nuxeo.s3utils.Constants.DEFAULT_HANDLER_NAME;
        }
        S3Handler s3Handler = s3HandlerService.getS3Handler(handlerName);
        if(StringUtils.isNotBlank(bucket)) {
            s3Handler.setBucket(bucket);
        }
        result = s3Handler.downloadFile(key, null);
        
        return result;
    }
    

}
