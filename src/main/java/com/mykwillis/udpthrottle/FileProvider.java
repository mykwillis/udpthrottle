package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.Message;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a file's contents as a number of byte array chunks, each prefixed with
 * a protocol-specific header.
 */
public class FileProvider {
    /**
     * Chunk size for network transmission.
     *
     * The chunk includes both the file payload, as well as a 12 byte header used
     * to specify the packet type, and the byte offset within the file of the
     * current chunk.
     *
     * The optimal chunk size would be determined by the specific application and
     * execution environment. In a production environment, we would want to test
     * various chunk sizes, and potentially choose the chunk size dynamically
     * depending on circumstances.
     */
    public static final int CHUNK_SIZE = 8192;
    public static final int CHUNK_HEADER_SIZE = 12;
    public static final int CHUNK_PAYLOAD_SIZE = CHUNK_SIZE - CHUNK_HEADER_SIZE;

    /**
     * A List of byte arrays, each array holding one chunk of file data prefixed with
     * the protocols-defined header. Each byte array is suitable for passing directly
     * to a DatagramPacket.
     */
    List<byte[]> chunks = new ArrayList<>();

    /**
     * Create a new FileProvider for the File given.
     *
     * @param file A file on the local filesystem. This file should be less than 10MB.
     */
    public FileProvider(File file) throws FileNotFoundException {
        FileInputStream inputStream = new FileInputStream(file);
        readFile(inputStream);
    }

    /**
     * Create a new FileProvider for the given InputStream.
     *
     * @param inputStream the stream that should be served to clients, which should
     *                    be less than 10MB in size.
     */
    public FileProvider(InputStream inputStream) {
        readFile(inputStream);
    }

    /**
     * Gets a chunk by index.
     *
     * Specifying an index greater than the number of chunks returns null.
     *
     * @param index The index to fetch.
     * @return a byte[] consisting of a 4 byte offset header followed by file data,
     *  or null if the requested index is out of bounds.
     *
     */
    public byte[] getChunk(int index) {
        if (index >= chunks.size()) {
            return null;
        }
        return chunks.get(index);
    }

    /**
     * Prepares a file for serving by loading it into memory.
     *
     * We load the file completely into memory, in chunks suitable for serving. We
     * could load the file contents dynamically while serving, but having it pre-
     * loaded makes it straightforward to serve to multiple clients simultaneously.
     * As we expect file size to be relatively small (<10MB), loading it ahead of time
     * shouldn't cause memory concerns.
     */
    void readFile(InputStream inputStream) {

        int bytesRead = 0;
        long offset = 0;
        try {
            do {
                byte[] chunk = new byte[CHUNK_SIZE];
                ByteBuffer byteBuffer = ByteBuffer.wrap(chunk);
                byteBuffer.putInt(Message.MESSAGE_TYPE_PAYLOAD);
                byteBuffer.putLong(offset);

                bytesRead = inputStream.read(chunk, CHUNK_HEADER_SIZE, CHUNK_PAYLOAD_SIZE);

                // The last read of a file may give us less than CHUNK_PAYLOAD_SIZE bytes.
                // In this case, we need to re-allocate the byte array to the proper size,
                // as the array size is used to determine how many bytes to transmit.
                if (bytesRead > 0 && bytesRead < CHUNK_PAYLOAD_SIZE) {
                    chunk = java.util.Arrays.copyOf(chunk, CHUNK_HEADER_SIZE + bytesRead);
                }
                if (bytesRead > 0) {
                    chunks.add(chunk);
                }
                offset += bytesRead;
            } while(bytesRead > 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
