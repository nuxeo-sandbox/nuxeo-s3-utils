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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.s3utils.CacheForKeyExists;
import org.nuxeo.s3utils.S3Handler;
import org.nuxeo.s3utils.S3HandlerDescriptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * CacheForKeyExists only needs an S3Handler to answer existsKeyInS3, so a stub is enough: these tests need neither the
 * Nuxeo runtime nor AWS and always run.
 *
 * @since 2025.1
 */
public class TestCacheForKeyExists {

    /** Counts the calls that reached "S3", and answers whatever we tell it to. */
    protected static class StubS3Handler implements S3Handler {

        protected final AtomicInteger callCount = new AtomicInteger();

        protected boolean answer = true;

        protected String bucket = "the-default-bucket";

        @Override
        public boolean existsKeyInS3(String inBucket, String inKey) {
            callCount.incrementAndGet();
            return answer;
        }

        @Override
        public boolean existsKeyInS3(String inKey) {
            return existsKeyInS3(null, inKey);
        }

        @Override
        public String getBucket() {
            return bucket;
        }

        @Override
        public void setBucket(String inBucket) {
            bucket = inBucket;
        }

        // ---- not used by CacheForKeyExists ----
        @Override
        public void initialize(S3HandlerDescriptor desc) {
        }

        @Override
        public void cleanup() {
        }

        @Override
        public S3Client getS3() {
            return null;
        }

        @Override
        public boolean sendFile(String inKey, File inFile) {
            return false;
        }

        @Override
        public boolean sendFile(String inBucket, String inKey, File inFile) {
            return false;
        }

        @Override
        public Blob downloadFile(String inKey, File inDestFile) {
            return null;
        }

        @Override
        public Blob downloadFile(String inBucket, String inKey, File inDestFile) {
            return null;
        }

        @Override
        public Blob downloadFile(String inKey, String inFileName) {
            return null;
        }

        @Override
        public Blob downloadFile(String inBucket, String inKey, String inFileName) {
            return null;
        }

        @Override
        public boolean deleteFile(String inKey) {
            return false;
        }

        @Override
        public boolean deleteFile(String inBucket, String inKey) {
            return false;
        }

        @Override
        public String buildPresignedUrl(String inKey, int durationInSeconds, String contentType,
                String contentDisposition) {
            return null;
        }

        @Override
        public String buildPresignedUrl(String inBucket, String inKey, int durationInSeconds, String contentType,
                String contentDisposition) {
            return null;
        }

        @Override
        public boolean existsKey(String inKey) {
            return false;
        }

        @Override
        public boolean existsKey(String bucket, String inKey) {
            return false;
        }

        @Override
        public HeadObjectResponse getObjectMetadata(String inKey) {
            return null;
        }

        @Override
        public JsonNode getObjectMetadataJson(String inKey) throws JsonProcessingException {
            return null;
        }

        @Override
        public int getSignedUrlDuration() {
            return 0;
        }

        @Override
        public SequenceInputStream getSequenceInputStream(String inKey, long pieceSize) throws IOException {
            return null;
        }

        @Override
        public byte[] readBytes(String key, long start, long len) throws IOException {
            return new byte[0];
        }
    }

    @Test
    public void shouldCacheAndNotCallS3Twice() {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);

        assertTrue(cache.existsKey("a-key"));
        assertEquals(1, handler.callCount.get());

        assertTrue(cache.existsKey("a-key"));
        assertEquals("The second call must be served by the cache", 1, handler.callCount.get());
        assertTrue(cache.isInCache("a-key"));
        assertEquals(1, cache.getCacheCount());
    }

    /**
     * Regression test: the cache key was the plain concatenation of the bucket and the object key, so ("a", "bc") and
     * ("ab", "c") shared the same entry and one bucket answered for the other.
     */
    @Test
    public void mustNotConfuseTwoBucketAndKeyPairsThatConcatenateTheSame() {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);

        handler.answer = true;
        assertTrue(cache.existsKey("bucket-a", "bc"));

        handler.answer = false;
        // Without a separator this reuses the entry above and wrongly answers true
        assertFalse(cache.existsKey("bucket-ab", "c"));

        assertEquals("The two pairs must be two distinct entries", 2, cache.getCacheCount());
        assertEquals(2, handler.callCount.get());
    }

    /**
     * Regression test: eviction removed 20% of maxInCache by hand, which rounds down to 0 for any maximum below 5, so
     * the cache grew without any limit.
     */
    @Test
    public void mustEvictEvenWithASmallMaximum() {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);
        cache.setMaxInCache(3);

        for (int i = 0; i < 50; i++) {
            cache.existsKey("key-" + i);
        }

        assertTrue("The cache grew past its maximum: " + cache.getCacheCount(), cache.getCacheCount() <= 3);
    }

    @Test
    public void shouldForgetAnExpiredEntry() throws Exception {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);
        cache.setDurationInCache(100);

        assertTrue(cache.existsKey("a-key"));
        assertEquals(1, handler.callCount.get());

        Thread.sleep(250);

        assertFalse(cache.isInCache("a-key"));
        assertTrue(cache.existsKey("a-key"));
        assertEquals("The expired entry must have been read from S3 again", 2, handler.callCount.get());
    }

    /**
     * Regression test: an S3Handler is a singleton shared by every caller, so its cache is reachable from several
     * threads at once. The two unsynchronized LinkedHashMaps it used could corrupt each other or loop forever.
     */
    @Test
    public void mustSurviveConcurrentAccess() throws Exception {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);
        cache.setMaxInCache(50);

        int threads = 8;
        int perThread = 400;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threads; t++) {
            int offset = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        cache.existsKey("bucket-" + (i % 3), "key-" + ((i + offset) % 120));
                        cache.isInCache("key-" + i);
                        cache.getCacheCount();
                    }
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            });
        }

        pool.shutdown();
        assertTrue("The concurrent run did not finish in time", pool.awaitTermination(60, TimeUnit.SECONDS));
        assertTrue("Concurrent access raised " + failures, failures.isEmpty());
        assertTrue("The cache grew past its maximum: " + cache.getCacheCount(), cache.getCacheCount() <= 50);
    }

    @Test
    public void cleanupShouldEmptyTheCache() {

        StubS3Handler handler = new StubS3Handler();
        CacheForKeyExists cache = new CacheForKeyExists(handler);

        cache.existsKey("a-key");
        assertEquals(1, cache.getCacheCount());

        cache.cleanup();
        assertEquals(0, cache.getCacheCount());
    }

}
