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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.MissingResourceException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assume;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;


/**
 * We don't want to hard code the bucket name or the distant object key, since everyone will have a different one.
 * There are two ways to inject the values for testing:
 * <ul>
 * <li>The (git ignored) <code>aws-test.conf</code> file at
 * <code>nuxeo-s3-utils-plugin/src/test/resources/</code>. Copy <code>aws-test.conf.sample</code>, which sits next to
 * it, and fill in your own values. This is the recommended way.</li>
 * <li>Environment variables, used when the file is missing. Each key has an upper snake case equivalent:
 * <code>test.aws.region</code> is read from <code>TEST_AWS_REGION</code>, <code>test.object.key</code> from
 * <code>TEST_OBJECT_KEY</code>, etc. (the original dotted names are still accepted, but a POSIX shell cannot export
 * them). This is what a CI job would use.</li>
 * </ul>
 * See <code>aws-test.conf.sample</code> for the list of keys and what each one means, and the README for the
 * authentication part. <b>No credential is ever read from this file</b>: they come from the standard AWS credentials
 * chain.
 * <p>
 * Whatever you choose, the properties are loaded and set as system properties, so the "default" S3Handler contribution
 * (see s3-utils-service.xml) will use them.
 * <p>
 * <b>IMPORTANT</b>: When the configuration or the credentials are missing, the tests do not fail, they are
 * <i>skipped</i> (see {@link TestUtils#assumeAwsIsAvailable()}). Run with
 * <code>-Ds3utils.test.requireAws=true</code> to turn these skips into failures.
 *
 * @since 8.1
 */
@Deploy("org.nuxeo.runtime.aws")
public class SimpleFeatureCustom implements RunnerFeature {

    public static final String TEST_CONF_FILE = "aws-test.conf";

    public static final String TEST_CONF_KEY_NAME_AWS_REGION = "test.aws.region";

    public static final String TEST_CONF_KEY_NAME_AWS_S3_BUCKET = "test.aws.s3.bucket";

    public static final String TEST_CONF_KEY_NAME_USE_CACHE = "test.use.cache";

    public static final String TEST_CONF_KEY_NAME_OBJECT_KEY = "test.object.key";

    public static final String TEST_CONF_KEY_NAME_OBJECT_SIZE = "test.object.size";

    public static final String TEST_CONF_KEY_NAME_OBJECT_MIMETYPE = "test.object.mimetype";

    public static final String TEST_CONF_KEY_NAME_IMAGE_KEY = "test.image.key";

    public static final String TEST_CONF_KEY_NAME_IMAGE_SIZE = "test.image.size";

    public static final String TEST_CONF_KEY_NAME_IMAGE_MIMETYPE = "test.image.mimetype";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_KEY = "test.bigobject.key";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_SIZE = "test.bigobject.size";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_MIMETYPE = "test.bigobject.mimetype";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_PIECE_SIZE = "test.bigobject.pieceSize";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_START = "test.bigobject.readbytes.start";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_LEN = "test.bigobject.readbytes.len";

    public static final String TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_VALUE = "test.bigobject.readbytes.value";

    public static final String TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY = "test.upload.file.key";

    protected static Properties props = null;

    public static String getLocalProperty(String key) {

        if (props != null) {
            return props.getProperty(key);
        }

        return null;
    }

    public static boolean hasLocalTestConfiguration() {
        return props != null;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {

        File file = null;
        FileInputStream fileInput = null;
        try {
            file = FileUtils.getResourceFileFromContext(TEST_CONF_FILE);
            if (file == null || !file.exists()) {
                // Not an error: the environment variables are then used instead
                System.out.println("No '" + TEST_CONF_FILE
                        + "' file in the test resources, looking for environment variables instead.");
                props = null;
            } else {
                fileInput = new FileInputStream(file);
                props = new Properties();
                props.load(fileInput);
            }

        } catch (Exception e) {
            // Do not fail silently: without this, every S3 test skips with no explanation
            System.err.println("Could not load the '" + TEST_CONF_FILE + "' test configuration file: " + e);
            props = null;
        } finally {
            if (fileInput != null) {
                try {
                    fileInput.close();
                } catch (IOException e) {
                    // Ignore
                }
                fileInput = null;
            }
        }

        if (props == null) {
            // Try to get environment variables
            addEnvironmentVariable(TEST_CONF_KEY_NAME_AWS_REGION);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_AWS_S3_BUCKET);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_USE_CACHE);

            addEnvironmentVariable(TEST_CONF_KEY_NAME_OBJECT_KEY);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_OBJECT_SIZE);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_OBJECT_MIMETYPE);

            addEnvironmentVariable(TEST_CONF_KEY_NAME_IMAGE_KEY);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_IMAGE_SIZE);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_IMAGE_MIMETYPE);

            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_KEY);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_SIZE);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_MIMETYPE);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_PIECE_SIZE);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_START);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_LEN);
            addEnvironmentVariable(TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_VALUE);

            addEnvironmentVariable(TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY);
        }

        if (props != null) {

            /*
             * Region and bucket are the only two mandatory values: without them the "default" S3Handler cannot even be
             * built. Rather than failing with a NullPointerException below (which is what a partial environment used to
             * produce), we report the problem and behave as if there were no configuration at all, so the tests skip
             * cleanly.
             */
            String region = props.getProperty(TEST_CONF_KEY_NAME_AWS_REGION);
            String bucket = props.getProperty(TEST_CONF_KEY_NAME_AWS_S3_BUCKET);
            if (StringUtils.isAnyBlank(region, bucket)) {
                System.err.println("The test configuration is incomplete, '" + TEST_CONF_KEY_NAME_AWS_REGION + "' and '"
                        + TEST_CONF_KEY_NAME_AWS_S3_BUCKET + "' are both required: the S3 tests will be skipped.");
                props = null;
                return;
            }

            Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_REGION, region);
            systemProps.setProperty(Constants.CONF_KEY_NAME_BUCKET, bucket);
            systemProps.setProperty(Constants.CONF_KEY_NAME_USECACHEFOREXISTSKEY,
                    props.getProperty(TEST_CONF_KEY_NAME_USE_CACHE, "false"));

            systemProps.setProperty("nuxeo.aws.s3utils.minimumUploadPartSize", "0");
            systemProps.setProperty("nuxeo.aws.s3utils.multipartUploadThreshold", "0");

        }
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {

        Properties p = System.getProperties();
        p.remove(Constants.CONF_KEY_NAME_REGION);
        p.remove(Constants.CONF_KEY_NAME_BUCKET);
        p.remove(Constants.CONF_KEY_NAME_USECACHEFOREXISTSKEY);
    }

    /**
     * Converts a configuration key to its environment variable equivalent, ie. <code>test.aws.region</code> becomes
     * <code>TEST_AWS_REGION</code>. A POSIX shell cannot export a variable whose name contains a dot, so the dotted
     * names alone made the fallback unusable.
     *
     * @since 2025.1
     */
    protected static String toEnvVarName(String key) {
        return key.toUpperCase().replace('.', '_');
    }

    protected void addEnvironmentVariable(String key) {
        // Upper snake case first, then the historical dotted name
        String value = System.getenv(toEnvVarName(key));
        if (value == null) {
            value = System.getenv(key);
        }
        if (value != null) {
            if (props == null) {
                props = new Properties();
            }
            props.put(key, value);
        }
    }

    public static class BigObjectInfo {
        public String key;

        public String mimeType;

        public long size;

        public long pieceSize;

        public long readBytesStart;

        public long readBytesLen;

        public String readBytesValue;

        public boolean ok;

        BigObjectInfo() {
            key = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_KEY);
            mimeType = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_MIMETYPE);
            String sizeStr = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_SIZE);
            String pieceSizeStr = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_PIECE_SIZE);
            String readBytesStartStr = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_START);
            String readBytesLenStr = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_LEN);
            readBytesValue = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_BIGOBJECT_READBYTES_VALUE);

            ok = !StringUtils.isAnyBlank(key, mimeType, sizeStr, pieceSizeStr, readBytesStartStr, readBytesLenStr,
                    readBytesValue);

            if (StringUtils.isNotBlank(sizeStr)) {
                size = Long.parseLong(sizeStr);
            }

            if (StringUtils.isNotBlank(pieceSizeStr)) {
                pieceSize = Long.parseLong(pieceSizeStr);
            }

            if (StringUtils.isNotBlank(readBytesStartStr)) {
                readBytesStart = Long.parseLong(readBytesStartStr);
            }

            if (StringUtils.isNotBlank(readBytesLenStr)) {
                readBytesLen = Long.parseLong(readBytesLenStr);
            }
        }

    }

}
