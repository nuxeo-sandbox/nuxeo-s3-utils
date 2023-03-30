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
import java.util.Properties;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.s3utils.Constants;

/**
 * We don't want to hard code the bucket name or the distant object key, since
 * everyone will have a different one. There are two ways to inject the values
 * for testing:
 * <ul>
 * <li>Use environment variables: Setup your environment and inject the expected
 * variables. This would be used when automating testing with maven for example
 * (passing the env. variables to maven)</li>
 *
 * <li>Use the (git ignored) "aws-test.conf" file:
 * <ul>
 * <li>We have a file named aws-test.conf at
 * nuxeo-s3utils-plugin/src/test/resources/</li>
 * <li>The file declares the region, the bucket, distant object key, ... using the
 * keys defined below (TEST_CONF_KEY_NAME_AWS_KEY_ID, etc.)</li>
 * <li>The .gitignore config file ignores this file, so it is not sent on
 * GitHub</li>
 * </ul>
 *
 * </li>
 * </ul>
 *
 * So, basically to run the test, create this file at
 * nuxeo-s3utils-plugin/src/test/resources/ and set the following properties:
 *
 * <pre>
 * {@code
 * #User: TestForPlugins
 * test.aws.region=eu-west-1
 * test.aws.s3.bucket=eu-west-1-demo-bucket
 * test.use.cache=true
 * #These files must exist in this bucket
 * test.object.key=used-in-unit-test-do-not-change.pdf
 * test.object.size=135377
 * test.object.mimetype=application/pdf
 * test.image.key=used-in-unit-test-do-not-change.jpg
 * test.image.size=879394
 * test.image.mimetype=image/jpeg
 * # For upload
 * test.upload.file.key=Brief.pdf
 * }
 * </pre>
 * </ul>
 *
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
			addEnvironmentVariable(TEST_CONF_KEY_NAME_UPLOAD_FILE_KEY);
		}

		if (props != null) {
		    
			Properties systemProps = System.getProperties();
            systemProps.setProperty(Constants.CONF_KEY_NAME_REGION,
                    props.getProperty(TEST_CONF_KEY_NAME_AWS_REGION));
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
		if(value != null) {
			if(props == null) {
				props = new Properties();
			}
			props.put(key, value);
		}
	}

}
