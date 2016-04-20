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
 *     Thibaud Arguillere
 */
package org.nuxeo.s3utils.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;
import org.nuxeo.s3utils.Constants;

/**
 * Important: To test the feature, we don't want to hard code the AWS keys (since this code could be published on GitHub
 * for example) and we don't want to hard code he bucket name or the distant object key, since everyone will have a
 * different one. So, the principles used are the following:
 * <ul>
 * <li>We have a file named aws-test.conf at nuxeo-s3utils-plugin/src/test/resources/</li>
 * <li>The file contains the keys, the bucket, distant object key, ...</li>
 * <li>The .gitignore config file ignores this file, so it is not sent on GitHub</li>
 * </ul>
 * So, basically to run the test, create this file at nuxeo-s3utils-plugin/src/test/resources/ and set the following
 * properties:
 * 
 * <pre>
 * {@code
 * test.aws.key=HERE_THE_KEY_ID
 * test.aws.secret=HERE_THE_SECRET_KEY
 * test.aws..s3.bucket=HERE_THE_NAME_OF_THE_BUCKET_TO_TEST
 * test.object=somefile.pdf
 * test.object.size=HERE_THE_EXACT_SIZE_OF_somefile.pdf
 * # For upload.
 * test.upload.file.key=Brief.pdf
 * }
 * </pre>
 * <p>
 * These properties will be loaded and set in the environment, so the "default" S3Handler contribution (see
 * s3-utils-service.xml) will use them.
 * 
 * @since 8.1
 */
public class SimpleFeatureCustom extends SimpleFeature {

    public static final String TEST_CONF_FILE = "aws-test.conf";

    public static final String TEST_CONF_KEY_NAME_AWS_KEY_ID = "test.aws.key";

    public static final String TEST_CONF_KEY_NAME_AWS_SECRET = "test.aws.secret";

    public static final String TEST_CONF_KEY_NAME_AWS_S3_BUCKET = "test.aws.s3.bucket";

    public static final String TEST_CONF_KEY_NAME_OBJECT_KEY = "test.object.key";

    public static final String TEST_CONF_KEY_NAME_OBJECT_SIZE = "test.object.size";

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

        if (props != null) {

            Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_ACCESS_KEY,
                    props.getProperty(TEST_CONF_KEY_NAME_AWS_KEY_ID));
            systemProps.setProperty(Constants.CONF_KEY_NAME_SECRET_KEY,
                    props.getProperty(TEST_CONF_KEY_NAME_AWS_SECRET));
            systemProps.setProperty(Constants.CONF_KEY_NAME_BUCKET, props.getProperty(TEST_CONF_KEY_NAME_AWS_S3_BUCKET));

        }
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {

        Properties p = System.getProperties();
        p.remove(Constants.CONF_KEY_NAME_ACCESS_KEY);
        p.remove(Constants.CONF_KEY_NAME_SECRET_KEY);
        p.remove(Constants.CONF_KEY_NAME_BUCKET);
    }

}
