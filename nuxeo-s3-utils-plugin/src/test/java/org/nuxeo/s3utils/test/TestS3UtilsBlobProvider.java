/*
 * (C) Copyright 2023 Hyland (http://hyland.com/)  and others.
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.platform.mimetype.service.MimetypeRegistryService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.s3utils.BlobKey;
import org.nuxeo.s3utils.Constants;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3UtilsBlobProvider;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.SequenceInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @since TODO
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SimpleFeatureCustom.class })
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.platform.convert")
@Deploy("nuxeo-s3-utils")
public class TestS3UtilsBlobProvider {

    @Inject
    CoreSession session;

    @Inject
    BlobManager blobManager;

    @Inject
    MimetypeRegistryService mimetypeService;

    @Inject
    ConversionService conversionService;

    @Inject
    TransactionalFeature transactionalFeature;

    protected static String TEST_FILE_KEY;

    protected static String TEST_FILE_NAME;

    protected static long TEST_FILE_SIZE = -1;

    protected static String TEST_FILE_MIMETYPE;

    protected String testBucket;

    protected S3Handler s3Handler;

    @Before
    public void setup() throws Exception {

        if (SimpleFeatureCustom.hasLocalTestConfiguration()) {
            // Sanity check
            TEST_FILE_KEY = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY);
            assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_KEY,
                    StringUtils.isNotBlank(TEST_FILE_KEY));
            
            TEST_FILE_NAME = FilenameUtils.getName(TEST_FILE_KEY);

            String sizeStr = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE);
            assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_SIZE,
                    StringUtils.isNotBlank(sizeStr));
            TEST_FILE_SIZE = Long.parseLong(sizeStr);

            TEST_FILE_MIMETYPE = SimpleFeatureCustom.getLocalProperty(
                    SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_MIMETYPE);
            assertTrue("Missing " + SimpleFeatureCustom.TEST_CONF_KEY_NAME_OBJECT_MIMETYPE,
                    StringUtils.isNotBlank(TEST_FILE_MIMETYPE));

            s3Handler = S3Handler.getS3Handler(Constants.DEFAULT_HANDLER_NAME);

        }
    }

    @Test
    public void testGetBlob() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        // Build a provider (not from XML)
        S3UtilsBlobProvider blobProvider = new S3UtilsBlobProvider();
        Map<String, String> properties = new HashMap<>();
        // Attached to the default handler
        properties.put(S3UtilsBlobProvider.S3_HANDLER_ATTACHED_PROPERTY, Constants.DEFAULT_HANDLER_NAME);
        // Other properties by default
        blobProvider.initialize("TestS3Provider", properties);
        blobManager.getBlobProviders().put("TestS3Provider", blobProvider);

        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey("TestS3Provider", s3Handler.getBucket(), TEST_FILE_KEY);
        info.filename = TEST_FILE_KEY;
        Blob blob = blobProvider.readBlob(info);
        File tmp = Framework.createTempFile("nxtmp-", "");
        FileUtils.copyInputStreamToFile(blob.getStream(), tmp);

        assertTrue(tmp.length() == TEST_FILE_SIZE);
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.convert")
    public void testGetBlobWithDownloadThreshold() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        // Build a provider (not from XML) with a download threshold
        S3UtilsBlobProvider blobProvider = new S3UtilsBlobProvider();
        Map<String, String> properties = new HashMap<>();
        // Attached to the default handler
        properties.put(S3UtilsBlobProvider.S3_HANDLER_ATTACHED_PROPERTY, Constants.DEFAULT_HANDLER_NAME);
        properties.put(S3UtilsBlobProvider.NO_DEFAULT_DOWNLOAD_ABOVE_PROPERTY, "" + TEST_FILE_SIZE / 2);
        // Other properties by default
        blobProvider.initialize("TestS3Provider", properties);
        blobManager.getBlobProviders().put("TestS3Provider", blobProvider);

        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey("TestS3Provider", s3Handler.getBucket(), TEST_FILE_KEY);
        info.filename = TEST_FILE_KEY;
        Blob blob = blobProvider.readBlob(info);
        assertNotNull(blob);
        // Do not set an extension
        File tmp = Framework.createTempFile("nxtmp-", "");
        FileUtils.copyInputStreamToFile(blob.getStream(), tmp);

        String finalMimetype = mimetypeService.getMimetypeFromFile(tmp);
        assertEquals(TEST_FILE_MIMETYPE, finalMimetype);

    }

    @Test
    public void testGetBlobImageWithDownloadThreshold() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        // Had issue deploying the pdf2image converter in unit test (working fine in live testing)
        // Issue fixed, but I still let this test here for a while.
        if (!conversionService.getRegistredConverters().contains("pdf2image")) {
            System.out.println(
                    "******************************\nConverter pdf2image not deployed in the unit test => skipping the testGetBlobImageWithDownloadThreshold test\n******************************");
            Assume.assumeTrue("Converter pdf2image not deployed", false);
        }

        // Load test image
        String imageKey = SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_IMAGE_KEY);
        Assume.assumeTrue("No image key in the configuration file", StringUtils.isNoneBlank(imageKey));

        String imageKeyimageSizeStr = SimpleFeatureCustom.getLocalProperty(
                SimpleFeatureCustom.TEST_CONF_KEY_NAME_IMAGE_SIZE);
        Assume.assumeTrue("No image size in the configuration file", StringUtils.isNoneBlank(imageKeyimageSizeStr));
        long imageKeyimageSize = Long.parseLong(imageKeyimageSizeStr);

        String imageMimetype = SimpleFeatureCustom.getLocalProperty(
                SimpleFeatureCustom.TEST_CONF_KEY_NAME_IMAGE_MIMETYPE);
        Assume.assumeTrue("No mimetype for the test image key in the configuration file",
                StringUtils.isNoneBlank(imageMimetype));

        // Build a provider (not from XML) with a download threshold
        S3UtilsBlobProvider blobProvider = new S3UtilsBlobProvider();
        Map<String, String> properties = new HashMap<>();
        // Attached to the default handler
        properties.put(S3UtilsBlobProvider.S3_HANDLER_ATTACHED_PROPERTY, Constants.DEFAULT_HANDLER_NAME);
        properties.put(S3UtilsBlobProvider.NO_DEFAULT_DOWNLOAD_ABOVE_PROPERTY, "" + imageKeyimageSize / 2);
        // Other properties by default
        blobProvider.initialize("TestS3Provider", properties);
        blobManager.getBlobProviders().put("TestS3Provider", blobProvider);

        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey("TestS3Provider", s3Handler.getBucket(), imageKey);
        info.filename = imageKey;
        Blob blob = blobProvider.readBlob(info);
        assertNotNull(blob);

        File f = blob.getFile();
        assertTrue(imageKeyimageSize > f.length());
        String finalMimetype = mimetypeService.getMimetypeFromFile(f);
        assertEquals(imageMimetype, finalMimetype);

    }

    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void testGetBlobWithXmlConfig() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(blobProvider);

        S3Handler xmlS3Handler = blobProvider.getS3Handler();
        assertNotNull(xmlS3Handler);

        BlobInfo info = new BlobInfo();
        info.key = BlobKey.buildFullKey(blobProvider.blobProviderId, xmlS3Handler.getBucket(), TEST_FILE_KEY);
        info.filename = TEST_FILE_KEY;
        Blob blob = blobProvider.readBlob(info);
        File tmp = Framework.createTempFile("nxtmp-", "");
        FileUtils.copyInputStreamToFile(blob.getStream(), tmp);

        Assert.assertTrue(tmp.length() == TEST_FILE_SIZE);
    }

    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void shouldCreateBlobFromKey() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(blobProvider);

        ManagedBlob b = blobProvider.createBlobFromObjectKey(TEST_FILE_KEY);
        assertNotNull(b);

        assertEquals(TEST_FILE_NAME, b.getFilename());
        assertEquals(TEST_FILE_SIZE, b.getLength());
        assertEquals(TEST_FILE_MIMETYPE, b.getMimeType());

        String expectedKey = BlobKey.buildFullKey("TestS3BlobProvider-XML",
                SimpleFeatureCustom.getLocalProperty(SimpleFeatureCustom.TEST_CONF_KEY_NAME_AWS_S3_BUCKET),
                TEST_FILE_KEY);
        assertEquals(expectedKey, b.getKey());

    }

    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void shouldHandleDocumentBlob() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        // Get the BlobProvider
        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(blobProvider);

        // Create the ManagedBlob and the doc
        ManagedBlob b = blobProvider.createBlobFromObjectKey(TEST_FILE_KEY);
        assertNotNull(b);
        DocumentModel doc = TestUtils.createDocWithBlob(session, transactionalFeature, b);

        // Test result
        Blob docBlob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(docBlob);
        assertTrue(docBlob instanceof ManagedBlob);

        ManagedBlob managedDocBlob = (ManagedBlob) docBlob;
        assertEquals(b.getKey(), managedDocBlob.getKey());

        // Check can download and the downloaded file is the one we want
        File f = managedDocBlob.getFile();
        assertTrue(f.exists());
        assertEquals(TEST_FILE_SIZE, f.length());
        assertEquals(TEST_FILE_NAME, f.getName());

    }

    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void shouldDownloadPlaceHolderForBigBlob() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        // Get the BlobProvider
        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider(
                "TestS3BlobProvider-withDownloadThreshold");
        assertNotNull(blobProvider);

        // Check we have a property OK for the test
        long maxForDownload = blobProvider.getMaxForDefaultDownload();
        assertTrue(maxForDownload > 0);
        // Check it is ok with the test file
        Assume.assumeTrue("The max threshold for download should be < " + TEST_FILE_SIZE,
                TEST_FILE_SIZE > maxForDownload);

        // Create the ManagedBlob and the doc
        ManagedBlob b = blobProvider.createBlobFromObjectKey(TEST_FILE_KEY);
        assertNotNull(b);
        DocumentModel doc = TestUtils.createDocWithBlob(session, transactionalFeature, b);

        // Test result
        Blob docBlob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(docBlob);
        assertTrue(docBlob instanceof ManagedBlob);

        ManagedBlob managedDocBlob = (ManagedBlob) docBlob;
        assertEquals(b.getKey(), managedDocBlob.getKey());

        File f = managedDocBlob.getFile();
        assertTrue(f.exists());
        // The blobprovider should have returned a smaller blob.
        assertTrue(f.length() < TEST_FILE_SIZE);

    }

    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void shouldStreamTheBigObject() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        SimpleFeatureCustom.BigObjectInfo boi = new SimpleFeatureCustom.BigObjectInfo();
        Assume.assumeTrue("No big object info in the configuration file", boi.ok);

        // Get the BlobProvider
        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(blobProvider);

        // Create the ManagedBlob and the doc
        ManagedBlob b = blobProvider.createBlobFromObjectKey(boi.key);
        assertNotNull(b);
        DocumentModel doc = TestUtils.createDocWithBlob(session, transactionalFeature, b);

        // Test result
        Blob docBlob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(docBlob);
        assertTrue(docBlob instanceof ManagedBlob);

        // As of today, We can't call the getStream() method of Blob, this behavior is standard and will return a stream
        // from a File (so, the S3 object is downloaded/cached). We must call the blobProvider custom getInputStream method
        SequenceInputStream stream = blobProvider.getSequenceInputStream(b);
        assertNotNull(stream);

        // Here we just check we have all the good amount
        long size = 0;
        long bytesLength;
        // Let's read "big" chunks
        int buffer = 500 * 1024;
        // Must be < pieceSize though
        if (boi.pieceSize > 0 && buffer >= boi.pieceSize) {
            buffer = (int) (boi.pieceSize / 2);
        }
        do {
            byte bytes[] = stream.readNBytes(buffer);
            bytesLength = bytes.length;
            size += bytesLength;
        } while (bytesLength > 0);
        assertEquals(boi.size, size);

    }
    
    @Test
    @Deploy("nuxeo-s3-utils:test-s3-blobprovider.xml")
    public void shouldReadBytesFromBigObject() throws Exception {

        Assume.assumeTrue("No custom configuration file => no test", SimpleFeatureCustom.hasLocalTestConfiguration());
        Assume.assumeTrue("Connection to AWS is failing. Are your credentials correctly set?",
                TestUtils.awsCredentialsLookOk());

        SimpleFeatureCustom.BigObjectInfo boi = new SimpleFeatureCustom.BigObjectInfo();
        Assume.assumeTrue("No big object info in the configuration file", boi.ok);

        // Get the BlobProvider
        S3UtilsBlobProvider blobProvider = (S3UtilsBlobProvider) blobManager.getBlobProvider("TestS3BlobProvider-XML");
        assertNotNull(blobProvider);

        // Create the ManagedBlob and the doc
        ManagedBlob b = blobProvider.createBlobFromObjectKey(boi.key);
        assertNotNull(b);
        DocumentModel doc = TestUtils.createDocWithBlob(session, transactionalFeature, b);

        // Test result
        Blob docBlob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(docBlob);
        assertTrue(docBlob instanceof ManagedBlob);
        
        byte[] bytes = blobProvider.readBytes(b, boi.readBytesStart, boi.readBytesLen);
        // Our test file is a pure text, change this unit test if it's different for you
        assertEquals(boi.readBytesLen, bytes.length);
        
        String resultStr = new String(bytes);
        assertEquals(boi.readBytesValue, resultStr);
        
    }

}
