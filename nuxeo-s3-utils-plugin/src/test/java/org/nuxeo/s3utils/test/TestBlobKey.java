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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.s3utils.BlobKey;

/**
 * BlobKey needs neither the Nuxeo runtime nor AWS, so these tests always run.
 *
 * @since 2025.1
 */
public class TestBlobKey {

    protected static final String PROVIDER = "MyS3Provider";

    protected static final String BUCKET = "my-bucket";

    @Test
    public void shouldParseAKey() {

        BlobKey key = new BlobKey(PROVIDER, BlobKey.buildFullKey(PROVIDER, BUCKET, "a/folder/file.pdf"));

        assertEquals(BUCKET, key.getBucket());
        assertEquals("a/folder/file.pdf", key.getObjectKey());
        // Regression: blobProviderId was never assigned, getProviderId() always returned null
        assertEquals(PROVIDER, key.getProviderId());
        assertTrue(key.isValid());
    }

    /**
     * Regression test: the key was split on every ":", so an object key containing one, which S3 allows, produced more
     * than 3 parts and was rejected as malformatted.
     */
    @Test
    public void shouldAcceptAnObjectKeyContainingAColon() {

        String objectKey = "a/folder/2026:03:14-report.pdf";
        BlobKey key = new BlobKey(PROVIDER, BlobKey.buildFullKey(PROVIDER, BUCKET, objectKey));

        assertEquals(BUCKET, key.getBucket());
        assertEquals(objectKey, key.getObjectKey());
    }

    /**
     * Regression test: the provider check used startsWith without the ":", so a provider accepted the keys of every
     * other provider whose id starts with its own id.
     */
    @Test
    public void shouldRejectAKeyOfAProviderWithTheSamePrefix() {

        String otherProviderKey = BlobKey.buildFullKey(PROVIDER + "Archive", BUCKET, "file.pdf");

        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, otherProviderKey));
    }

    @Test
    public void shouldRejectAMalformattedKey() {

        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, PROVIDER + ":only-two-parts"));
        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, null));
        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, ""));
    }

    @Test
    public void shouldRejectAKeyMissingBucketOrObjectKey() {

        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, PROVIDER + "::file.pdf"));
        assertThrows(NuxeoException.class, () -> new BlobKey(PROVIDER, PROVIDER + ":" + BUCKET + ":"));
    }

}
