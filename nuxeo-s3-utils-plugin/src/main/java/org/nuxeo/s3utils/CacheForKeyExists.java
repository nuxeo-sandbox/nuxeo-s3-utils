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
package org.nuxeo.s3utils;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * This class caches S3 keys and their existence on S3 for a given S3Handler. This is to avoid checking a key too often
 * <p>
 * An S3Handler is a singleton shared by every caller, so this cache is reachable from several threads at once: every
 * access to the underlying map must be synchronized.
 *
 * @since 7.10
 */
public class CacheForKeyExists {

    protected static final int MAX_KEYS = 500;

    protected static final int DURATION_IN_CACHE_MS = 600000; // 10 minutes (in milliseconds)

    /**
     * A bucket name cannot contain ":" (see the AWS bucket naming rules), so it is a safe separator: without one,
     * ("a", "bc") and ("ab", "c") would share the same cache entry.
     *
     * @since 2025.1
     */
    protected static final String CACHE_KEY_SEPARATOR = ":";

    /**
     * What we know about a key, and when we learned it.
     *
     * @since 2025.1
     */
    protected record CacheEntry(boolean exists, long since) {
    }

    protected String defaultBucket;

    protected int maxInCache = MAX_KEYS;

    protected int durationInCache = DURATION_IN_CACHE_MS;

    /**
     * Access ordered map whose eldest entry is dropped once the maximum is reached, which gives us a LRU for free.
     * <p>
     * Always access it inside a {@code synchronized (cachedKeys)} block.
     */
    protected final Map<String, CacheEntry> cachedKeys = new LinkedHashMap<>(16, 0.75f, true) {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > maxInCache;
        }
    };

    protected S3Handler s3Handler;

    public CacheForKeyExists(S3Handler handler) {

        s3Handler = handler;

        defaultBucket = s3Handler.getBucket();
    }

    /**
     * This method <b>must</i> be called to properly cleanup memory and release cross references to the S3Handler
     *
     * @since 8.2
     */
    public void cleanup() {

        synchronized (cachedKeys) {
            cachedKeys.clear();
        }

        s3Handler = null;
    }

    protected String buildCachekey(String bucket, String objectKey) {
        if (StringUtils.isBlank(bucket)) {
            bucket = defaultBucket;
        }
        return bucket + CACHE_KEY_SEPARATOR + objectKey;
    }

    /*
     * Returns -1 if the key is not in the cache, 0 if it is in the cache and does not exist on S3, and 1 if it is in
     * the cache and exists on S3
     */
    protected int existsKeyCheckInCache(String cacheKey) {

        if (StringUtils.isBlank(cacheKey)) {
            return -1;
        }

        synchronized (cachedKeys) {
            CacheEntry entry = cachedKeys.get(cacheKey);
            if (entry == null) {
                return -1;
            }
            if ((System.currentTimeMillis() - entry.since()) >= durationInCache) {
                cachedKeys.remove(cacheKey);
                return -1;
            }
            return entry.exists() ? 1 : 0;
        }
    }

    protected void addToCachedKeys(String cacheKey, boolean exists) {

        if (StringUtils.isBlank(cacheKey)) {
            return;
        }

        synchronized (cachedKeys) {
            /*
             * Drop what has expired, then let removeEldestEntry do the capping. The previous code purged 20% of
             * maxInCache by hand, which rounded down to 0 for a maximum below 5 and let the cache grow without limit.
             */
            long timeNow = System.currentTimeMillis();
            cachedKeys.entrySet().removeIf(e -> (timeNow - e.getValue().since()) >= durationInCache);

            cachedKeys.put(cacheKey, new CacheEntry(exists, timeNow));
        }
    }

    /**
     * Returns true is the key in the bucket is in the cache. If the bucket is empty, uses the "current bucket"
     *
     * @param bucket
     * @param objectKey
     * @return true if (bucket + key) are in the cache
     * @since 8.2
     */
    public boolean isInCache(String bucket, String objectKey) {

        String cacheKey = buildCachekey(bucket, objectKey);
        return existsKeyCheckInCache(cacheKey) > -1;
    }

    /**
     * Returns true is the key in the bucket is in the cache. Uses the "current bucket"
     *
     * @param objectKey
     * @return true if (bucket + key) are in the cache
     * @since 8.2
     */
    public boolean isInCache(String objectKey) {
        return isInCache(null, objectKey);
    }

    /**
     * Checks if the key exists on S3, after first checking if it is in the cache
     * <p>
     * See {@link existsKey(String bucket, String objectKey)}
     *
     * @param objectKey
     * @return true is the key exists on S3
     * @since 8.1
     */
    public boolean existsKey(String objectKey) {

        return existsKey(null, objectKey);
    }

    /**
     * Checks if the key exists on S3:
     * <ul>
     * <li>First, checks the key in the cache</li>
     * <li>If not found, checks on S3 and adds the info to the cache</li>
     * </ul>
     *
     * @param objectKey
     * @return true is the key exists on S3
     * @since 8.1
     */
    public boolean existsKey(String bucket, String objectKey) {

        boolean exists = false;

        if (StringUtils.isNotBlank(objectKey)) {

            if (StringUtils.isBlank(bucket)) {
                bucket = defaultBucket;
            }

            String bucketAndKey = buildCachekey(bucket, objectKey);
            int inCache = existsKeyCheckInCache(bucketAndKey);
            if (inCache != -1) {
                exists = inCache == 1;
            } else {
                // Never setBucket() here: the handler is a singleton shared by every caller, so changing its
                // bucket would silently repoint every other caller to this bucket
                exists = s3Handler.existsKeyInS3(bucket, objectKey);
                addToCachedKeys(bucketAndKey, exists);
            }
        }

        return exists;
    }

    /**
     * Returns the number of elements in the cache.
     *
     * @return the number of elements in the cache
     * @since 8.2
     */
    public int getCacheCount() {
        synchronized (cachedKeys) {
            return cachedKeys.size();
        }
    }

    /**
     * Set the bucket to use by default. If inBucket is empty, value is reset to the bucket stored in the S33Handler
     *
     * @param inBucket
     * @since 8.2
     */
    public void setBucket(String inBucket) {
        defaultBucket = StringUtils.isBlank(inBucket) ? s3Handler.getBucket() : inBucket;
    }

    /**
     * Returns the maximum objects stored in the cache. Default is {@link MAX_KEYS}
     *
     * @return the maximum objects stored in the cache
     * @since 8.2
     */
    public int getMaxInCache() {
        return maxInCache;
    }

    /**
     * If maxInCache is <= 0, the default value applies
     *
     * @param maxInCache
     * @since 8.2
     */
    public void setMaxInCache(int maxInCache) {
        this.maxInCache = maxInCache <= 0 ? MAX_KEYS : maxInCache;
    }

    /**
     * Returns the durations of an object in the cache, in milliseconds
     *
     * @return the duration in the cache
     * @since 8.2
     */
    public int getDurationInCache() {
        return durationInCache;
    }

    /**
     * If durationInCacheMillisecs is <= 0, the default value applies ({@link DURATION_IN_CACHE_MS})
     *
     * @param durationInCache
     * @since 8.1
     */
    public void setDurationInCache(int durationInCacheMillisecs) {
        durationInCache = durationInCacheMillisecs <= 0 ? DURATION_IN_CACHE_MS : durationInCacheMillisecs;
    }

}
