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
package org.nuxeo.s3utils.test;

import static org.junit.Assert.*;

import java.io.File;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.operations.S3DownloadOp;
import org.nuxeo.s3utils.operations.S3GetObjectMetadataOp;
import org.nuxeo.s3utils.operations.S3KeyExistsOp;
import org.nuxeo.s3utils.operations.S3TempSignedUrlOp;
import org.nuxeo.s3utils.operations.S3UploadOp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

/**
 * See {@link SimpleFeatureCustom} for explanation about the local configuration
 * file used for testing.
 *
 * @since 7.10
 */
@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, SimpleFeatureCustom.class })
@Deploy({ "nuxeo-s3-utils" })
public class TestOperations {

	protected static S3Handler s3Handler;

	protected static String TEST_FILE_KEY = null;

	protected static String UPLOAD_KEY = null;

	protected static long TEST_FILE_SIZE = -1;

	protected static final String FILE_TO_UPLOAD = "Brief.pdf";

	@Inject
	CoreSession coreSession;

	@Inject
	AutomationService automationService;

	@Before
	public void setup() throws Exception {

		if (SimpleFeatureCustom.hasLocalTestConfiguration()) {
			// Sanity check
			TEST_FILE_KEY = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY);
			assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY,
					StringUtils.isNotBlank(TEST_FILE_KEY));

			String sizeStr = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE);
			assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE,
					StringUtils.isNotBlank(sizeStr));
			TEST_FILE_SIZE = Long.parseLong(sizeStr);

			UPLOAD_KEY = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY);
			assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY,
					StringUtils.isNotBlank(UPLOAD_KEY));

			s3Handler = S3Handler.getS3Handler(Constants.DEFAULT_HANDLER_NAME);
		}

	}

	@After
	public void cleanup() {

		deleteTestFileOnS3();
	}

	protected void deleteTestFileOnS3() {

		if (StringUtils.isNotBlank(UPLOAD_KEY)) {
			try {
				s3Handler.deleteFile(UPLOAD_KEY);
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	@Test
	public void testUpload() throws Exception {

		Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

		// Delete in case it already exist from an interrupted previous test
		deleteTestFileOnS3();

		boolean exists = s3Handler.existsKeyInS3(UPLOAD_KEY);
		assertFalse(exists);

		File file = FileUtils.getResourceFileFromContext(FILE_TO_UPLOAD);
		Blob blob = new FileBlob(file);

		OperationChain chain;
		OperationContext ctx = new OperationContext(coreSession);
		ctx.setInput(blob);
		chain = new OperationChain("testWithDefault");
		chain.add(S3UploadOp.ID).set("key", UPLOAD_KEY);
		Blob result = (Blob) automationService.run(ctx, chain);
		Assert.assertNotNull(result);

		// Check exists
		exists = s3Handler.existsKeyInS3(UPLOAD_KEY);
		assertTrue(exists);

		// Delete
		deleteTestFileOnS3();

	}

	@Test
	public void uploadShouldFailWithWrongParameters() throws Exception {

		Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

		// Delete in case it already exist from an interrupted previous test
		deleteTestFileOnS3();

		boolean exists = s3Handler.existsKeyInS3(UPLOAD_KEY);
		assertFalse(exists);

		File file = FileUtils.getResourceFileFromContext(FILE_TO_UPLOAD);
		Blob blob = new FileBlob(file);

		OperationChain chain;
		OperationContext ctx = new OperationContext(coreSession);
		ctx.setInput(blob);
		chain = new OperationChain("testWithDefault");
		chain.add(S3UploadOp.ID).set("key", UPLOAD_KEY).set("handlerName", "WRONG_HANDLER_NAME");
		try {
			@SuppressWarnings("unused")
			Blob result = (Blob) automationService.run(ctx, chain);
			assertTrue("SHould have fail with a wrong S3Handler name", false);
		} catch (OperationException e) {
			// All good
		}

	}

	@Test
	public void testDownload() throws Exception {

		Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

		OperationChain chain;
		OperationContext ctx = new OperationContext(coreSession);
		chain = new OperationChain("testWithDefault");
		chain.add(S3DownloadOp.ID).set("key", TEST_FILE_KEY);
		Blob result = (Blob) automationService.run(ctx, chain);
		Assert.assertNotNull(result);

		File f = result.getFile();
		assertTrue(f.exists());

		String name = result.getFilename();
		long size = result.getLength();
		assertEquals(TEST_FILE_KEY, name);
		assertEquals(TEST_FILE_SIZE, size);

	}

	@Test
	public void testKeyExists() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testExistsKey-1");
        chain.add(S3KeyExistsOp.ID).set("key", TEST_FILE_KEY);
        automationService.run(ctx, chain);

        assertTrue((boolean) ctx.get(S3KeyExistsOp.RESULT_CONTEXT_VAR_NAME));

	}

    @Test
    public void testKeyDoesNotExist() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        String invalid = UUID.randomUUID().toString().replace("-", "") + ".pdf";

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testExistsKey-1");
        chain.add(S3KeyExistsOp.ID).set("key", invalid);
        automationService.run(ctx, chain);

        assertFalse((boolean) ctx.get(S3KeyExistsOp.RESULT_CONTEXT_VAR_NAME));

    }

    @Test
    public void testTempSignedUrl() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testExistsKey-1");
        chain.add(S3TempSignedUrlOp.ID).set("key", TEST_FILE_KEY);
        automationService.run(ctx, chain);

        String urlStr = (String) ctx.get(S3TempSignedUrlOp.RESULT_CONTEXT_VAR_NAME);
        assertTrue(StringUtils.isNotBlank(urlStr));

        // We must be able to download the file without authentication
        File f = TestUtils.downloadFile(urlStr);
        assertNotNull(f);
        String name = f.getName();
        long size = f.length();
        // Cleanup now
        f.delete();

        assertEquals(TEST_FILE_KEY, name);
        assertEquals(TEST_FILE_SIZE, size);

    }

    @Test
    public void testTempSignedUrShouldFaill() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        int duration = 2; // 2 seconds

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testExistsKey-1");
        chain.add(S3TempSignedUrlOp.ID).set("key", TEST_FILE_KEY).set("durationInSeconds", 2);
        automationService.run(ctx, chain);

        String urlStr = (String) ctx.get(S3TempSignedUrlOp.RESULT_CONTEXT_VAR_NAME);
        assertTrue(StringUtils.isNotBlank(urlStr));

        // Wait for at least the duration
        Thread.sleep((duration + 1) * 1000);

        // Downloading should fail, so the returned File is null
        File f = TestUtils.downloadFile(urlStr);
        assertNull(f);

    }
    
    @Test
    public void testGetObjectMetadata() throws Exception {
        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testGetObjectMetadata-1");
        chain.add(S3GetObjectMetadataOp.ID).set("key", TEST_FILE_KEY);
        
        Blob result = (Blob) automationService.run(ctx, chain);
        assertNotNull(result);
        
        JSONObject obj = new JSONObject(result.getString());
        String str = obj.getString("Content-Type");
        assertEquals("application/pdf", str);
        
        long length = obj.getLong("Content-Length");
        assertEquals(TEST_FILE_SIZE, length);
        
        str = obj.getString("ETag");
        assertTrue(StringUtils.isNoneBlank(str));
        
    }
    
    @Test
    public void testGetObjectMetadataShouldNotFindKey() throws Exception {
        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        OperationChain chain;
        OperationContext ctx = new OperationContext(coreSession);
        chain = new OperationChain("testGetObjectMetadata-1");
        chain.add(S3GetObjectMetadataOp.ID).set("key", UUID.randomUUID().toString());
        
        Blob result = (Blob) automationService.run(ctx, chain);
        assertNotNull(result);        
        
        assertEquals("{}", result.getString());
        
    }

}
