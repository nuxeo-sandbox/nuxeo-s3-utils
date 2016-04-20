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
package org.nuxeo.s3utils.test;

import static org.junit.Assert.*;

import java.io.File;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.s3utils.CacheForKeyExists;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3HandlerService;

/**
 * See {@link SimpleFeatureCustom} for explanation about the local configuration file used for testing.
 * 
 * @since 7.10
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SimpleFeatureCustom.class })
@Deploy({ "nuxeo-s3-utils" })
public class TestS3Handler {

    protected static S3Handler s3Handler;

    protected static String TEST_FILE_KEY;

    protected static long TEST_FILE_SIZE = -1;

    protected static final String FILE_TO_UPLOAD = "Brief.pdf";

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
            
            S3HandlerService shs = (S3HandlerService) Framework.getService(S3HandlerService.class);
            s3Handler = shs.getS3Handler(Constants.DEFAULT_HANDLER_NAME);
        }

    }

    @Test
    public void testDownloadFile() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
                
        Blob result = s3Handler.downloadFile(TEST_FILE_KEY, null);
        assertNotNull(result);

        File f = result.getFile();
        assertTrue(f.exists());

        String name = result.getFilename();
        long size = result.getLength();
        assertEquals(TEST_FILE_KEY, name);
        assertEquals(TEST_FILE_SIZE, size);

    }

    @Test
    public void testUploadAndDelete() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        String uploadKey = null;
        uploadKey = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY);
        Assume.assumeTrue("No parameter for upload/delet test => no test", StringUtils.isNotBlank(uploadKey));

        // Delete in case it already exist from an interrupted previous test
        try {
            s3Handler.deleteFile(uploadKey);
        } catch (Exception e) {
            // Ignore
        }

        // Create
        File file = FileUtils.getResourceFileFromContext(FILE_TO_UPLOAD);
        boolean ok = s3Handler.sendFile(uploadKey, file);
        assertTrue(ok);

        // Check exists
        boolean exists = s3Handler.existsKeyInS3(uploadKey);
        assertTrue(exists);

        // Delete
        boolean deleted = s3Handler.deleteFile(uploadKey);
        assertTrue(deleted);

        // Does not exist no more
        exists = s3Handler.existsKeyInS3(uploadKey);
        assertFalse(exists);

    }

    @Test
    public void testExistsKeyInS3() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        boolean ok;

        ok = s3Handler.existsKeyInS3(TEST_FILE_KEY);
        assertTrue(ok);

        String invalid = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        ok = s3Handler.existsKeyInS3(invalid);
        assertFalse(ok);

    }
    
    @Test
    public void testCacheForKeyExists() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        
        boolean exists, isInCache;
        
        CacheForKeyExists cache = new CacheForKeyExists(s3Handler);
        
        // Get an object. Found or not, it should go in the cache
        exists = cache.existsKey(TEST_FILE_KEY);
        assertTrue(exists);
        
        isInCache = cache.isInCache(TEST_FILE_KEY);
        assertTrue(isInCache);
        
        isInCache = cache.isInCache(UUID.randomUUID().toString());
        assertFalse(isInCache);
        
        cache.cleanup();
        
        // Now, test duration
        int durationInCacheMs = 1000;
        cache = new CacheForKeyExists(s3Handler);
        cache.setDurationInCache(durationInCacheMs);

        exists = cache.existsKey(TEST_FILE_KEY);
        assertTrue(exists);
        
        // Wait for at least the duration
        Thread.sleep(durationInCacheMs + 1000);
        
        isInCache = cache.isInCache(TEST_FILE_KEY);
        assertFalse(isInCache);
        
    }

}
