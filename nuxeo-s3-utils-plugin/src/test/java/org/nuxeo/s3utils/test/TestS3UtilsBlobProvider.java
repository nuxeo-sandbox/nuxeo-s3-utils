/*
 * (C) Copyright 2023 Hyland (http://hyland.com/)  and others.
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
package org.nuxeo.s3utils.test;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.s3utils.BlobKey;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3UtilsBlobProvider;

import javax.inject.Inject;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @since TODO
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SimpleFeatureCustom.class })
@Deploy({ "nuxeo-s3-utils" })
public class TestS3UtilsBlobProvider {

    @Inject
    CoreSession session;

    @Inject
    BlobManager blobManager;
    
    protected static String TEST_FILE_KEY;

    protected static long TEST_FILE_SIZE = -1;
    
    protected String testBucket;

    protected S3Handler s3Handler;
    
    protected S3UtilsBlobProvider blobProvider;

    @Before
    public void setup() throws Exception {

        if(SimpleFeatureCustom.hasLocalTestConfiguration()) {
            // Sanity check
            TEST_FILE_KEY = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY);
            assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY,
                    StringUtils.isNotBlank(TEST_FILE_KEY));

            String sizeStr = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE);
            assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE,
                    StringUtils.isNotBlank(sizeStr));
            TEST_FILE_SIZE = Long.parseLong(sizeStr);

            s3Handler = S3Handler.getS3Handler(Constants.DEFAULT_HANDLER_NAME);
            
            blobProvider = new S3UtilsBlobProvider();
            Map<String,String> properties = new HashMap<>();
            // Attached to the default handler
            properties.put(S3UtilsBlobProvider.S3_HANDLER_ATTACHED_PROPERTY, Constants.DEFAULT_HANDLER_NAME);
            // Other properties by default
            blobProvider.initialize("TestS3Provider", properties);
            blobManager.getBlobProviders().put("TestS3Provider", blobProvider);
            
        }
    }
    
    @Test
    public void testGetBlob() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey("TestS3Provider", s3Handler.getBucket(), TEST_FILE_KEY);
        info.filename = TEST_FILE_KEY;
        Blob blob = blobProvider.readBlob(info);
        File tmp = Framework.createTempFile("nxtmp-", "");
        FileUtils.copyInputStreamToFile(blob.getStream(),tmp);

        assertTrue(tmp.length() == TEST_FILE_SIZE);
    }
    
    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void testGetBlobWithXmlConfig() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        S3UtilsBlobProvider xmlProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(xmlProvider);
        
        S3Handler xmlS3Handler = xmlProvider.getS3Handler();
        assertNotNull(xmlS3Handler);
        
        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey(xmlProvider.blobProviderId, xmlS3Handler.getBucket(), TEST_FILE_KEY);
        info.filename = TEST_FILE_KEY;
        Blob blob = xmlProvider.readBlob(info);
        File tmp = Framework.createTempFile("nxtmp-", "");
        FileUtils.copyInputStreamToFile(blob.getStream(),tmp);

        Assert.assertTrue(tmp.length() == TEST_FILE_SIZE);
    }

}
