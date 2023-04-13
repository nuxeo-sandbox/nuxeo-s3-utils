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

import com.amazonaws.SdkClientException;

/**
 * We don't want to hard code the bucket name or the distant object key, since
 * everyone will have a different one. There are two ways to inject the values
 * for testing:
 * <ul>
 * <li>Use environment variables: Setup your environment and inject the expected
 * variables. This would be used when automating testing with maven for example
 * (passing the env. variables to maven)</li>
 * <li>Use the (git ignored) "aws-test.conf" file:
 * <ul>
 * <li>We have a file named aws-test.conf at
 * nuxeo-s3utils-plugin/src/test/resources/</li>
 * <li>The file declares the region, the bucket, distant object key, ... using the
 * keys defined below (TEST_CONF_KEY_NAME_AWS_KEY_ID, etc.)</li>
 * <li>The .gitignore config file ignores this file, so it is not sent on
 * GitHub</li>
 * </ul>
 * </li>
 * </ul>
 * So, basically to run the test, create this file at
 * nuxeo-s3utils-plugin/src/test/resources/ and set the following properties:
 *
 * <pre>
 * {@code
 * test.aws.region=the-region
 * test.aws.s3.bucket=the-bucket
 * test.use.cache=true
 * 
 * #This file exists in this bucket
 * test.object.key=used-in-unit-test-do-not-change.pdf
 * test.object.size=135377
 * test.object.mimetype=application/pdf
 * test.image.key=used-in-unit-test-do-not-change.jpg
 * test.image.size=879394
 * test.image.mimetype=image/jpeg
 * 
 * # This file is for the test of the SequenceInputStream
 * # 11.1MB, chunck of 1MB
 * test.bigobject.key=used-in-unit-test-do-not-change-big.txt
 * test.bigobject.size=10264037
 * test.bigobject.mimetype=text/plain
 * test.bigobject.pieceSize=1048576
 * test.bigobject.readbytes.start=5000000
 * test.bigobject.readbytes.len=22
 * test.bigobject.readbytes.value=HERE-THE_CHARS-TO-FIND
 * 
 * # For upload
 * test.upload.file.key=Brief.pdf
 * }
 * </pre>
 * </ul>
 * Whatever you choose, the properties will be loaded and set in the
 * environment, so the "default" S3Handler contribution (see
 * s3-utils-service.xml) will use them.
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
            fileInput = new FileInputStream(file);
            props = new Properties();
            props.load(fileInput);

        } catch (Exception e) {
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

            Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_REGION, props.getProperty(TEST_CONF_KEY_NAME_AWS_REGION));
            systemProps.setProperty(Constants.CONF_KEY_NAME_BUCKET,
                    props.getProperty(TEST_CONF_KEY_NAME_AWS_S3_BUCKET));
            systemProps.setProperty(Constants.CONF_KEY_NAME_USECACHEFOREXISTSKEY,
                    props.getProperty(TEST_CONF_KEY_NAME_USE_CACHE));

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

    protected void addEnvironmentVariable(String key) {
        String value = System.getenv(key);
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
