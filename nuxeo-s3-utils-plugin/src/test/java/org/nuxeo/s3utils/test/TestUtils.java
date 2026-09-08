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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assume;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;

public class TestUtils {

    private static final Logger log = LogManager.getLogger(TestUtils.class);

    public static int credentialsLookOk = -1;

    /**
     * When this system property is <code>true</code>, a missing configuration file or missing AWS credentials makes the
     * tests <i>fail</i> instead of being silently skipped.
     * <p>
     * Use it whenever you need the guarantee that the S3 code really ran:
     *
     * <pre>
     * mvn test -Ds3utils.test.requireAws=true
     * </pre>
     *
     * @since 2025.1
     */
    public static final String REQUIRE_AWS_PROPERTY = "s3utils.test.requireAws";

    /**
     * @return true if the caller asked for the S3 tests to fail rather than skip
     * @since 2025.1
     */
    public static boolean requireAws() {
        return Boolean.parseBoolean(System.getProperty(REQUIRE_AWS_PROPERTY, "false"));
    }

    /**
     * Single entry point guarding every test that needs a real S3 connection.
     * <p>
     * By default a missing configuration or missing credentials <i>skips</i> the test, which means a green build does
     * not prove the S3 code was exercised: always check for "Skipped: 0" in the surefire output. Run with
     * <code>-D{@value #REQUIRE_AWS_PROPERTY}=true</code> to turn those skips into failures.
     *
     * @since 2025.1
     */
    public static void assumeAwsIsAvailable() {

        boolean hasConfiguration = SimpleFeatureCustom.hasLocalTestConfiguration();
        String noConfMessage = "No custom configuration file (" + SimpleFeatureCustom.TEST_CONF_FILE
                + ") and no environment variable => no test";
        String noCredentialsMessage = "Connection to AWS is failing. Are your credentials correctly set?";

        if (requireAws()) {
            assertTrue(noConfMessage, hasConfiguration);
            assertTrue(noCredentialsMessage, awsCredentialsLookOk());
        } else {
            Assume.assumeTrue(noConfMessage, hasConfiguration);
            Assume.assumeTrue(noCredentialsMessage, awsCredentialsLookOk());
        }
    }

    /**
     * We return fails only if we get an error related to credentials while trying to connect to an s3 bucket.
     * In all other cases we return true.
     *
     * @return
     * @since 2.1.1
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
                    s3Handler.getS3().headBucket(b -> b.bucket(bucket));
                } catch (SdkClientException e) {
                    /*
                     * A SdkClientException (as opposed to a service exception) means the request never reached AWS.
                     * Failing to resolve credentials is the case we care about here.
                     */
                    String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                    if (message.contains("unable to load credentials")
                            || message.contains("unable to load aws credentials")) {
                        credentialsLookOk = 0;
                    }
                } catch (SdkException e) {
                    // A service error (404 no such bucket, 403, ...) means the credentials were usable
                }
            } else {
                log.info("The local '{}' configuration file is missing: cannot check the AWS connection.",
                        SimpleFeatureCustom.TEST_CONF_FILE);
            }
        }

        return credentialsLookOk == 1;

    }

    /**
     * The returned file is a temp file. Still, caller should delete it once done dealing with it
     */
    public static File downloadFile(String url) throws IOException {

        File resultFile = null;

        // new URL(String) is deprecated since Java 20
        URL theURL = URI.create(url).toURL();
        HttpURLConnection http = (HttpURLConnection) theURL.openConnection();

        if (http.getResponseCode() == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = http.getHeaderField("Content-Disposition");

            if (disposition != null) {
                fileName = fileNameFromContentDisposition(disposition);
            } else {
                // extracts file name from URL
                fileName = url.substring(url.lastIndexOf("/") + 1);
                int idx = fileName.indexOf("?");
                if (idx > -1) {
                    fileName = fileName.substring(0, idx);
                }
            }
            /*
             * The name comes from a remote header or from the URL: keep the base name only, so that it can never
             * escape the temporary directory. Nuxeo temporary files must be created below the Nuxeo temporary
             * directory, hence Framework.createTempDirectory and not java.io.tmpdir.
             */
            fileName = FilenameUtils.getName(fileName);
            if (StringUtils.isEmpty(fileName)) {
                fileName = "DownloadedFile-" + UUID.randomUUID();
            }

            File tempDir = Framework.createTempDirectory("s3utils-download-").toFile();
            resultFile = new File(tempDir, fileName);

            try (InputStream inputStream = http.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(resultFile)) {
                IOUtils.copy(inputStream, outputStream);
            }
        }

        return resultFile;
    }


    /**
     * Extracts the file name from a Content-Disposition header.
     * <p>
     * Since Nuxeo 2025.18, <code>RFC2231.encodeContentDisposition</code> follows RFC 6266 and emits <i>both</i>
     * parameters when the name needs encoding:
     *
     * <pre>
     * attachment; filename="a/b.pdf"; filename*=UTF-8''a%2Fb.pdf
     * </pre>
     *
     * Older Nuxeo versions emitted only the <code>filename*</code> parameter. We read <code>filename*</code> first
     * (it is the authoritative one per RFC 6266) and fall back to <code>filename</code>, then keep only the base
     * name, since the object key can contain a path.
     *
     * @since 2025.1
     */
    protected static String fileNameFromContentDisposition(String disposition) {

        String fileName = "";

        int index = disposition.indexOf("filename*=");
        if (index > -1) {
            String value = disposition.substring(index + "filename*=".length()).trim();
            // Cut at the next parameter, if any
            int end = value.indexOf(';');
            if (end > -1) {
                value = value.substring(0, end);
            }
            // Strip the charset'language' prefix, ie. UTF-8''
            int quote = value.lastIndexOf('\'');
            if (quote > -1) {
                value = value.substring(quote + 1);
            }
            fileName = URLDecoder.decode(value, StandardCharsets.UTF_8);
        } else {
            index = disposition.indexOf("filename=");
            if (index > -1) {
                String value = disposition.substring(index + "filename=".length()).trim();
                int end = value.indexOf(';');
                if (end > -1) {
                    value = value.substring(0, end);
                }
                fileName = StringUtils.strip(value.trim(), "\"");
            }
        }

        // The key may be a path, we only want the file name
        return FilenameUtils.getName(fileName);
    }

    /**
     * Utility to avoid copy/pasting the same code everywhere.
     * The code:
     * - Creates the DocumentModel,
     * - attach the blob to file:content,
     * - Wait for async work (TransactionalFeature)
     * - Refresh and return the doc
     *
     * @param session
     * @param transactionalFeature
     * @param ManagedBlob
     * @return
     * @throws Exception
     * @since 2.1.1
     */
    public static DocumentModel createDocWithBlob(CoreSession session, TransactionalFeature transactionalFeature,
            ManagedBlob blob) throws Exception {

        // Create document, assign the blob
        DocumentModel doc = session.createDocumentModel("/", "testfile", "File");
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);

        // Wait for async stuff
        transactionalFeature.nextTransaction();

        // Refresh and check
        doc = session.getDocument(doc.getRef());
        return doc;
    }
}
