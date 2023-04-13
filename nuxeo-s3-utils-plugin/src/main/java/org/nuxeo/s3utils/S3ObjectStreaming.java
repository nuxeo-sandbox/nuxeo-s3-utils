package org.nuxeo.s3utils;

import java.io.IOException;
import java.io.SequenceInputStream;

public interface S3ObjectStreaming {

    /**
     * Get a SequenceInputStream to the object. The goal of using a SequenceInputStream is to avoid time out while
     * reading large, big objects.
     * <br>
     * pieceSize is the size in bytes for each sequential stream. It set the number of streams created (object size /
     * pieceSize). If 0, a default value is used.
     * Streams are open/close one after the other.
     * <br>
     * The caller can call close() any time, this will close all the streams.
     * <br>
     * See S3ObjectSequentialStream for more info.
     * 
     * @param key
     * @param pieceSize
     * @return the SequenceInputStream
     * @throws IOException
     * @since 2021.35
     */
    public SequenceInputStream getSequenceInputStream(String inKey, long pieceSize) throws IOException;

    /**
     * Read len bytes from start in the object.
     *
     * @param key
     * @param len
     * @return
     * @throws IOException
     * @since TODO
     */
    public byte[] readBytes(String key, long start, long len) throws IOException;

}
