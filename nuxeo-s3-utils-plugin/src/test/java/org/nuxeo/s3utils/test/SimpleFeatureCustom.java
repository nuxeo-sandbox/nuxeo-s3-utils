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
 * @since 8.1
 */
public class SimpleFeatureCustom extends SimpleFeature {

    // These are the properties that are tested. You must declare/use the same in your testconf.conf file
    protected static boolean localTestConfigurationOk = false;

    protected static Properties localProperties = null;

    public static String getLocalProperty(String key) {

        if (localProperties != null) {
            return localProperties.getProperty(key);
        }

        return null;
    }

    public static boolean hasLocalTestConfiguration() {
        return localTestConfigurationOk;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {

        localProperties = ConfigParametersForTest.loadProperties();

        if (localProperties != null) {

            Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_ACCESS_KEY,
                    localProperties.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_KEY_ID));
            systemProps.setProperty(Constants.CONF_KEY_NAME_SECRET_KEY,
                    localProperties.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_SECRET));
            systemProps.setProperty(Constants.CONF_KEY_NAME_BUCKET,
                    localProperties.getProperty(ConfigParametersForTest.TEST_CONF_KEY_NAME_AWS_S3_BUCKET));

            localTestConfigurationOk = true;

        } else {
            localTestConfigurationOk = false;
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
