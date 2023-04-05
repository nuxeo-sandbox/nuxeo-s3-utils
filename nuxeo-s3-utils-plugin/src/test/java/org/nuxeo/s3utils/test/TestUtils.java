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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;

import com.amazonaws.SdkClientException;

public class TestUtils {

    public static int credentialsLookOk = -1;

    /**
     * We return fails only if we get an error related to credentials while trying to connect to an s3 bucket.
     * In all other cases we return true.
     * 
     * @return
     * @since TODO
     */
    public static boolean awsCredentialsLookOk() {

        if (credentialsLookOk == -1) {
            
            credentialsLookOk = 1;
            if (SimpleFeatureCustom.hasLocalTestConfiguration()) {
                String bucket = (String) SimpleFeatureCustom.getLocalProperty(
                        SimpleFeatureCustom.TEST_CONF_KEY_NAME_AWS_S3_BUCKET);
                S3Handler s3Handler = S3Handler.getS3Handler(Constants.DEFAULT_HANDLER_NAME);

                try {
                    // We don't care if the bucket does not exist, we check only credentials
                    s3Handler.getS3().doesBucketExistV2(bucket);
                } catch (SdkClientException e) {
                    if (e.getMessage()
                         .toLowerCase()
                         .startsWith("Unable to load AWS credentials from any provider in the chain")) {
                        credentialsLookOk = 0;
                    }
                }
            } else {
                System.out.println("The local '" + SimpleFeatureCustom.TEST_CONF_FILE
                        + "' configuration file is missing: Cannot check AWS connection ");
            }
        }
        
        return credentialsLookOk == 1;

    }

    /**
     * The returned file is a temp file. Still, caller should delete it once done dealing with it
     */
    public static File downloadFile(String url) throws IOException {

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
                int idx = fileName.indexOf("?");
                if (idx > -1) {
                    fileName = fileName.substring(0, idx);
                }
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
