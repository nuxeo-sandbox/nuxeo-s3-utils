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
package org.nuxeo.s3utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

/**
 * This class caches S3 keys for a given S3Handler. This is to avoid checking a key "every second"
 * 
 * @since 7.10
 */
public class CacheForKeyExists {

    protected static final int MAX_KEYS = 500;

    protected static final int DURATION_IN_CACHE_MS = 600000; // 10 minutes (in milliseconds)

    protected String defaultBucket;

    protected int maxInCache = MAX_KEYS;

    protected int durationInCache = DURATION_IN_CACHE_MS;

    protected LinkedHashMap<String, Boolean> cachedKeysAndExist = new LinkedHashMap<String, Boolean>();

    protected LinkedHashMap<String, Long> cachedKeysAndSince = new LinkedHashMap<String, Long>();

    protected S3Handler s3Handler;

    public CacheForKeyExists(S3Handler handler) {

        s3Handler = handler;

        defaultBucket = s3Handler.getBucket();
    }

    /**
     * This method <b>must</i> be called to properly cleanup memory and release cross reference to the S3Handler
     * 
     * @since 8.2
     */
    public void cleanup() {

        cachedKeysAndExist = null;
        cachedKeysAndSince = null;

        s3Handler = null;
    }

    protected String buildCachekey(String bucket, String objectKey) {
        if(StringUtils.isBlank(bucket)) {
            bucket = defaultBucket;
        }
        return bucket + objectKey;
    }

    /*
     * Returns -1 if the key is not in the cache, 0 if it is in the cache and does not exist on S3, and 1 if it is in
     * the cache and exists on S3
     */
    protected int existsKeyCheckInCache(String cacheKey) {

        int result = -1;

        if (StringUtils.isNotBlank(cacheKey)) {
            Boolean exists = cachedKeysAndExist.get(cacheKey);
            if (exists != null) {
                Long since = cachedKeysAndSince.get(cacheKey);
                long timeNow = System.currentTimeMillis();

                if ((timeNow - since) >= durationInCache) {
                    cachedKeysAndExist.remove(cacheKey);
                    cachedKeysAndSince.remove(cacheKey);
                } else {
                    result = exists ? 1 : 0;
                }
            }
        }

        return result;

    }

    protected void addToCachedKeys(String cacheKey, boolean exists) {

        if (StringUtils.isNotBlank(cacheKey)) {
            if (cachedKeysAndExist.size() >= maxInCache) {

                Entry<String, Long> current;
                long timeNow = System.currentTimeMillis();
                Iterator<Map.Entry<String, Long>> it = cachedKeysAndSince.entrySet().iterator();
                while (it.hasNext()) {
                    current = it.next(); // Must be called before removing
                    Long since = current.getValue();
                    if ((timeNow - since) >= durationInCache) {
                        it.remove();
                        cachedKeysAndExist.remove(current.getKey());
                    }
                }

                // Still something? Remove a few...
                if (cachedKeysAndExist.size() >= maxInCache) {
                    int howMany = (int) (maxInCache * 0.2);
                    String[] keys = new String[howMany];
                    Iterator<Map.Entry<String, Boolean>> iterator = cachedKeysAndExist.entrySet().iterator();
                    for (int i = 0; i < howMany; ++i) {
                        keys[i] = iterator.next().getKey();
                    }

                    for (String oneKey : keys) {
                        cachedKeysAndExist.remove(oneKey);
                        cachedKeysAndSince.remove(oneKey);
                    }
                }
            }

            cachedKeysAndExist.put(cacheKey, exists);
            cachedKeysAndSince.put(cacheKey, System.currentTimeMillis());

        }
    }

    public boolean isInCache(String bucket, String objectKey) {

        String cacheKey = buildCachekey(bucket, objectKey);
        return existsKeyCheckInCache(cacheKey) > -1;
    }

    public boolean isInCache(String objectKey) {
        return isInCache(null, objectKey);
    }

    /**
     * Checks if the key is in the cache. If yes, returns the stored true/false value. If not, adds it after checking on
     * S3
     * 
     * @param objectKey
     * @return
     * @since 8.1
     */
    public boolean existsKey(String objectKey) {

        return existsKey(null, objectKey);
    }

    /**
     * Checks if the key is in the cache. If yes, returns the stored true/false value. If not, adds it after checking on
     * S3
     * 
     * @param objectKey
     * @return
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
                s3Handler.setBucket(bucket);
                exists = s3Handler.existsKeyInS3(objectKey);
                addToCachedKeys(bucketAndKey, exists);
            }
        }

        return exists;
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

    public int getMaxInCache() {
        return maxInCache;
    }

    /**
     * If maxInCache is <= 0, the default value applies
     * 
     * @param durationInCache
     * @since 8.1
     */
    public void setMaxInCache(int maxInCache) {
        this.maxInCache = maxInCache <= 0 ? MAX_KEYS : maxInCache;
    }

    public int getDurationInCache() {
        return durationInCache;
    }

    /**
     * If durationInCacheMillisecs is <= 0, the default value applies
     * 
     * @param durationInCache
     * @since 8.1
     */
    public void setDurationInCache(int durationInCacheMillisecs) {
        this.durationInCache = durationInCacheMillisecs <= 0 ? DURATION_IN_CACHE_MS : durationInCacheMillisecs;
    }

}
