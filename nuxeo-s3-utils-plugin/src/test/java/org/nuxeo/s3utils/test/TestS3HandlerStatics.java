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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nuxeo.s3utils.S3Handler;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * The static helpers of S3Handler need neither the Nuxeo runtime nor AWS, so these tests always run.
 *
 * @since 2025.1
 */
public class TestS3HandlerStatics {

    protected static S3Exception s3Exception(int statusCode, String errorCode) {
        return (S3Exception) S3Exception.builder()
                                        .statusCode(statusCode)
                                        .awsErrorDetails(AwsErrorDetails.builder()
                                                                        .errorCode(errorCode)
                                                                        .serviceName("S3")
                                                                        .build())
                                        .message("test")
                                        .build();
    }

    @Test
    public void aMissingKeyIsAMissingKey() {

        assertTrue(S3Handler.errorIsMissingKey(s3Exception(404, "NoSuchKey")));
        assertTrue(S3Handler.errorIsMissingKey(s3Exception(404, "NotFound")));
        // A HEAD request has no response body, so the SDK cannot fill the error code
        assertTrue(S3Handler.errorIsMissingKey(s3Exception(404, null)));
        assertTrue(S3Handler.errorIsMissingKey(NoSuchKeyException.builder().statusCode(404).message("m").build()));
    }

    /**
     * Regression test: AWS answers 404 for a missing bucket too. Reporting it as a missing key hides a configuration
     * error behind a plain "the object does not exist".
     */
    @Test
    public void aMissingBucketIsNotAMissingKey() {

        assertFalse(S3Handler.errorIsMissingKey(s3Exception(404, "NoSuchBucket")));
        assertFalse(S3Handler.errorIsMissingKey(
                (NoSuchBucketException) NoSuchBucketException.builder()
                                                             .statusCode(404)
                                                             .awsErrorDetails(AwsErrorDetails.builder()
                                                                                             .errorCode("NoSuchBucket")
                                                                                             .build())
                                                             .message("m")
                                                             .build()));
    }

    /**
     * Regression test: any other error means the check itself failed. It must not be confused with a missing key,
     * because existsKeyInS3 used to answer "the object exists" in that case.
     */
    @Test
    public void anyOtherErrorIsNotAMissingKey() {

        assertFalse(S3Handler.errorIsMissingKey(s3Exception(403, "AccessDenied")));
        assertFalse(S3Handler.errorIsMissingKey(s3Exception(500, "InternalError")));
        assertFalse(S3Handler.errorIsMissingKey(SdkClientException.builder().message("network is down").build()));
    }

}
