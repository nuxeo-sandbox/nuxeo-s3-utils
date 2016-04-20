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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.lang.StringUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
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
public class TestS3TempSignedUrl {

    protected static String TEST_FILE_KEY;

    protected static long TEST_FILE_SIZE = -1;

    protected static S3Handler s3Handler;

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
    public void testGetTempSignedUrl() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        String urlStr = s3Handler.buildPresignedUrl(TEST_FILE_KEY, 0, null, "filename=" + TEST_FILE_KEY);
        assertTrue(StringUtils.isNotBlank(urlStr));

        // We must be able to download the file without authentication
        File f = downloadFile(urlStr);
        assertNotNull(f);
        // Delete it now
        String name = f.getName();
        long size = f.length();
        f.delete();

        assertEquals(TEST_FILE_KEY, name);
        assertEquals(TEST_FILE_SIZE, size);

    }

    @Test
    public void testTempSignedUrlShouldFail() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());

        int duration = 2; // 2 seconds, not 20 minutes or whatever S3TempSignedURLBuilder.DEFAULT_EXPIRE is

        String urlStr = s3Handler.buildPresignedUrl(TEST_FILE_KEY, duration, null, "filename=" + TEST_FILE_KEY);
        assertTrue(StringUtils.isNotBlank(urlStr));

        // Wait for at least the duration
        Thread.sleep((duration + 1) * 1000);

        // Downloading should fail, so the returned File is null
        File f = downloadFile(urlStr);
        assertNull(f);

    }

    /*
     * The returned file is a temp file. Still, caller should delete it once done dealing with it
     */
    protected File downloadFile(String url) throws IOException {

        File resultFile = null;

        HttpURLConnection http = null;
        int BUFFER_SIZE = 4096;

        URL theURL = new URL(url);

        http = (HttpURLConnection) theURL.openConnection();
        // HTTPUtils.addHeaders(http, headers, headersAsJSON);

        if (http.getResponseCode() == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = http.getHeaderField("Content-Disposition");

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > -1) {
                    fileName = disposition.substring(index + 9);
                }
            } else {
                // extracts file name from URL
                fileName = url.substring(url.lastIndexOf("/") + 1, url.length());
            }
            if (StringUtils.isEmpty(fileName)) {
                fileName = "DownloadedFile-" + java.util.UUID.randomUUID().toString();
            }

            String tempDir = System.getProperty("java.io.tmpdir");
            resultFile = new File(tempDir, fileName);

            FileOutputStream outputStream = new FileOutputStream(resultFile);
            InputStream inputStream = http.getInputStream();
            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
        }

        return resultFile;
    }

}
