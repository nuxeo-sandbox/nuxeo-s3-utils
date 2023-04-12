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
package org.nuxeo.s3utils;

import java.io.SequenceInputStream;
import java.util.Enumeration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

/**
 * Enumeration to be used in a java SequenceInputStream, for big object streaming, when streaming may lead to connection
 * issue (typically, a timeout). Using this class, a sequential loop is created, and one after the other, a stream is
 * open for a certain range (see below), then closed when exhausted and a new stream is opened for the next etc. The
 * caller just gets a single, continuous S3ObjectInputStream
 * <br>
 * Say the object size is 10,000, and the piece size is 1,000 (using small values for the sake of the example. a
 * 1,500bytes objects would work fine with direct call to getObjectContent()), S3ObjectSequentialStream#getInputStream
 * will:
 * <ul>
 * <li>Open a stream for the 0-999 first bytes</li>
 * <li>Once exhausted, it is closed and another stream reads 1000-1999</li>
 * <li>Once exhausted, it is closed and another stream reads 2000-2999</li>
 * <li>etc.</li>
 * </ul>
 * Default value for the pieceSize is 100MB.<br>
 * Example of use:
 * 
 * <pre>
 * {@code
 *     S3ObjectSequentialStream seqStream = new S3ObjectSequentialStream(s3, "my-bucket", "bigbig-file.mov");
 *     InputStream stream = seqStream.getInputStream();
 *     // Now, you can read the stream, for example:
 *     byte[] bytes = new byte[100000];
 *     // . . . Loop on stream.read(bytes) . . .
 * }
 * </pre>
 * 
 * Thanks to Alex Chan and its original scala code (https://alexwlchan.net/2019/streaming-large-s3-objects/)
 * 
 * @since 2021.35
 */
public class S3ObjectSequentialStream implements Enumeration<S3ObjectInputStream> {

    protected static final Log log = LogFactory.getLog(S3ObjectSequentialStream.class);

    protected AmazonS3 s3;

    protected String objectKey;

    protected String bucket;

    protected long currentPosition = 0;

    protected long totalSize;

    protected long pieceSize = S3Handler.DEFAULT_PIECE_SIZE;

    public S3ObjectSequentialStream(AmazonS3 s3, String bucket, String objectKey) {
        this(s3, bucket, objectKey, 0);
    }

    public S3ObjectSequentialStream(AmazonS3 s3, String bucket, String objectKey, long pieceSize) {

        this.s3 = s3;
        this.bucket = bucket;
        this.objectKey = objectKey;
        if (pieceSize > 0) {
            this.pieceSize = pieceSize;
        }

        ObjectMetadata metadata = s3.getObjectMetadata(bucket, objectKey);
        totalSize = metadata.getContentLength();

    }

    @Override
    public boolean hasMoreElements() {
        return currentPosition < totalSize;
    }

    @Override
    public S3ObjectInputStream nextElement() {

        GetObjectRequest gor = new GetObjectRequest(bucket, objectKey).withRange(currentPosition,
                currentPosition + pieceSize - 1);
        currentPosition += pieceSize;

        S3ObjectInputStream stream = s3.getObject(gor).getObjectContent();
        
        return stream;
    }

    public SequenceInputStream getInputStream() {

        return new SequenceInputStream(this);
    }

}
