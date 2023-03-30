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
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3UtilsBlobProvider;

/**
 * Creates a blob referencing a S3 object
 *
 * @since 2.1.1
 */
@Operation(id = S3BlobProviderCreateBlobForObjectKeyOp.ID, category = Constants.CAT_BLOB, label = "S3 Utils: Create Blob from Object Key", description = ""
        + "Expects as required parameters a providerId (a provider that uses the org.nuxeo.s3utils.S3UtilsBlobProvider class)"
        + " and the key of an object in S3. Returns a blob that references this object.<br>"
        + "Cf. the documentation: the BlobProvider is linked to a S3Handler that knows which bucket to use.")
public class S3BlobProviderCreateBlobForObjectKeyOp {

    public static final String ID = "S3Utils.CreateBlobFromObjectKey";
    
    @Context
    BlobManager blobManager;

    @Param(name = "blobProviderId", required = true)
    protected String blobProviderId;

    @Param(name = "objectKey", required = true)
    protected String objectKey;

    @OperationMethod
    public Blob run() throws IOException {

        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider(blobProviderId);
        if(blobProvider == null) {
            throw new NuxeoException("The S3UtilsBlobProvider with id '" + blobProviderId + "' is not found. Didi you contribute it in your XML?");
        }
        
        Blob b = blobProvider.createBlobFromObjectKey(objectKey);
        return b;
        
    }

}
