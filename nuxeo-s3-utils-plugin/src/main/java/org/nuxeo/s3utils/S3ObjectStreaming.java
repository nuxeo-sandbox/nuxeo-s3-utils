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

import java.io.IOException;
import java.io.SequenceInputStream;

/**
 * Reads an S3 object without downloading it, either as a continuous stream or by range.
 *
 * @since 2021.35
 */
public interface S3ObjectStreaming {

    /**
     * Gets a SequenceInputStream to the object. The goal of using a SequenceInputStream is to avoid a time out while
     * reading large, big objects.
     * <p>
     * {@code pieceSize} is the size in bytes of each sequential stream. It sets the number of streams created (object
     * size / pieceSize). If 0, a default value is used. Streams are opened and closed one after the other.
     * <p>
     * The caller can call close() at any time, this closes all the streams.
     * <p>
     * See S3ObjectSequentialStream for more info.
     *
     * @param inKey the object key
     * @param pieceSize the size in bytes of each sequential stream, 0 to use the default
     * @return the SequenceInputStream
     * @throws IOException if the object cannot be read
     * @since 2021.35
     */
    public SequenceInputStream getSequenceInputStream(String inKey, long pieceSize) throws IOException;

    /**
     * Reads {@code len} bytes from {@code start} in the object.
     *
     * @param key the object key
     * @param start the position of the first byte to read
     * @param len the number of bytes to read
     * @return the bytes read
     * @throws IOException if the object cannot be read
     * @since 2021.35
     */
    public byte[] readBytes(String key, long start, long len) throws IOException;

}
