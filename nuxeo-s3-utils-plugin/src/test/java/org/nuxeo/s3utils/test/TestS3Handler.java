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
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3HandlerServiceImpl;

import com.google.inject.Inject;

/**
 * Important: To test the feature, we don't want to hard code the AWS keys (since this code could be published on GitHub
 * for example) and we don't want to hard code he bucket name or the distant object key, since everyone will have a
 * different one. So, the principles used are the following:
 * <ul>
 * <li>We have a file named aws-test.conf at nuxeo-s3utils-plugin/src/test/resources/</li>
 * <li>The file contains the keys</li>
 * <li>The .gitignore config file ignores this file, so it is not sent on GitHub</li>
 * </ul>
 * This configuration file also stores the key of the file to test, and it's info. So, basically to run the test, create
 * this file at nuxeo-s3utils-plugin/src/test/resources/ and set the following properties:
 * 
 * <pre>
 * {@code
 * test.aws.key=HERE_THE_KEY_ID
 * test.aws.secret=HERE_THE_SECRET_KEY
 * test.aws..s3.bucket=HERE_THE_NAME_OF_THE_BUCKET_TO_TEST
 * test.object=Creative-Brief-Lorem-ipsum.pdf
 * test.object.size = 39119
 * }
 * </pre>
 * 
 * @since 7.10
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SimpleFeatureCustom.class })
@Deploy({ "nuxeo-s3-utils" })
public class TestS3Handler {

    protected static String awsKeyId;

    protected static String awsSecret;

    protected static String awsBucket;

    protected static String TEST_FILE_KEY;

    protected static long TEST_FILE_SIZE = -1;

    protected static S3Handler s3Handler;

    protected static final String FILE_TO_UPLOAD = "Brief.pdf";

    Properties props = null;
    
    @Inject
    protected S3HandlerServiceImpl s3HandlerService;

    @Before
    public void setup() throws Exception {

        /*
        props = ConfigParametersForTest.loadProperties();
        if (props != null) {
            // Check we do have the keys
            if (StringUtils.isBlank(awsKeyId)) {
                awsKeyId = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_KEY_ID);
                assertTrue("Missing " + ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_KEY_ID,
                        StringUtils.isNotBlank(awsKeyId));
            }

            if (StringUtils.isBlank(awsSecret)) {
                awsSecret = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_SECRET);
                assertTrue("Missing " + ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_SECRET,
                        StringUtils.isNotBlank(awsSecret));
            }

            if (StringUtils.isBlank(awsBucket)) {
                awsBucket = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_S3_BUCKET);
                assertTrue("Missing " + ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_S3_BUCKET,
                        StringUtils.isNotBlank(awsBucket));
            }

            Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_ACCESS_KEY, awsKeyId);
            systemProps.setProperty(Constants.CONF_KEY_NAME_SECRET_KEY, awsSecret);
            systemProps.setProperty(Constants.CONF_KEY_NAME_BUCKET, awsBucket);

            // Now the file to test
            if (StringUtils.isBlank(TEST_FILE_KEY)) {
                TEST_FILE_KEY = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_OBJECT_KEY);
                assertTrue("Missing " + ConfigParametersForTest.TEST_CONF_KEY_NAME_OBJECT_KEY,
                        StringUtils.isNotBlank(TEST_FILE_KEY));
            }

            if (TEST_FILE_SIZE == -1) {
                String sizeStr = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_OBJECT_SIZE);
                assertTrue("Missing " + ConfigParametersForTest.TEST_CONF_KEY_NAME_OBJECT_SIZE,
                        StringUtils.isNotBlank(sizeStr));
                TEST_FILE_SIZE = Long.parseLong(sizeStr);
            }

            //s3Handler = new S3Handler(awsKeyId, awsSecret, awsBucket);
            s3Handler = s3HandlerService.getS3Handler(Constants.DEFAULT_HANDLER_NAME);
        }
        */
        
        if(SimpleFeatureCustom.hasLocalTestConfiguration()) {
            s3Handler = s3HandlerService.getS3Handler(Constants.DEFAULT_HANDLER_NAME);
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

        Assume.assumeTrue("No custom configuration file => no test", ConfigParametersForTest.hasLocalConfFile());

        String uploadKey = null;
        uploadKey = props.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY);
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
        boolean exists = s3Handler.existsKey(uploadKey);
        assertTrue(exists);

        // Delete
        boolean deleted = s3Handler.deleteFile(uploadKey);
        assertTrue(deleted);

        // Does not exist no more
        exists = s3Handler.existsKey(uploadKey);
        assertFalse(exists);

    }

    @Test
    public void testExistsKey() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", ConfigParametersForTest.hasLocalConfFile());

        boolean ok;

        ok = s3Handler.existsKey(TEST_FILE_KEY);
        assertTrue(ok);

        String invalid = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        ok = s3Handler.existsKey(invalid);
        assertFalse(ok);

    }

}
