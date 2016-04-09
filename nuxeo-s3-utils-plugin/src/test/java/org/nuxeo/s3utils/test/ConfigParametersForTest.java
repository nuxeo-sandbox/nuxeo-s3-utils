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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.nuxeo.common.utils.FileUtils;

/**
 * @since 8.1
 */
public class ConfigParametersForTest {

    public static final String TEST_CONF_FILE = "aws-test.conf";

    public static final String TEST_CONF_KEY_NAME_AWS_KEY_ID = "test.aws.key";

    public static final String TEST_CONF_KEY_NAME_AWS_SECRET = "test.aws.secret";

    public static final String TEST_CONF_KEY_NAME_AWS_S3_BUCKET = "test.aws.s3.bucket";

    public static final String TEST_CONF_KEY_NAME_OBJECT_KEY = "test.object.key";

    public static final String TEST_CONF_KEY_NAME_OBJECT_SIZE = "test.object.size";

    public static final String TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY = "test.upload.file.key";

    private static Properties props = null;

    private static int status = -1;

    /**
     * Returns the Properties after loading the local configuration file.
     * <p>
     * If the file is not found, return null
     * <p>
     * 
     * @return the Properties or null
     * @throws IOException
     * @since 8.1
     */
    public static Properties loadProperties() {

        if (status > -1) {
            return props;
        }

        File file = null;
        FileInputStream fileInput = null;
        try {
            file = FileUtils.getResourceFileFromContext(TEST_CONF_FILE);
            fileInput = new FileInputStream(file);
            props = new Properties();
            props.load(fileInput);

            status = 1;

        } catch (Exception e) {
            status = 0;
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

        return props;
    }

    public static boolean hasLocalConfFile() {
        return loadProperties() != null;
    }

}
