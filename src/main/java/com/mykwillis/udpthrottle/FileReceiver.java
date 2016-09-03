package com.mykwillis.udpthrottle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static java.lang.System.currentTimeMillis;

/**
 * Manages the receipt of file contents from the Server.
 *
 * This class is used by the Client to receive each packet (chunk) of data that
 * comes in from the server, and save it to the appropriate offset in the output
 * file.
 */
public class FileReceiver {
    private File file;
    private RandomAccessFile outputFile;
    private long expectedSize;
    private boolean[] chunksReceived;
    private int numberOfChunksReceived;
    private int expectedChunks;
    private long startTime;

    static int getExpectedChunks(long expectedSize) {
        return (int) (expectedSize + FileProvider.CHUNK_PAYLOAD_SIZE - 1) / FileProvider.CHUNK_PAYLOAD_SIZE;
    }

    static int getChunkIndexByPosition(long position) {
        return (int) position / (FileProvider.CHUNK_PAYLOAD_SIZE);
    }

    FileReceiver(String filename, long expectedSize) throws IOException {
        file = new File(filename);
        outputFile = new RandomAccessFile(file, "rw");
        outputFile.setLength(expectedSize);
        this.expectedSize = expectedSize;

        expectedChunks = getExpectedChunks(expectedSize);
        chunksReceived = new boolean[expectedChunks];   // default to false
    }

    void processPacket(byte[] bytes, int length) throws IOException {
        if (startTime == 0) {
            startTime = currentTimeMillis();
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        int packetType = byteBuffer.getInt();
        long position = byteBuffer.getLong();
        int payloadSize = length - FileProvider.CHUNK_HEADER_SIZE;
        if (position + payloadSize > expectedSize) {
            throw new IOException("Invalid position in packet.");
        }

        int chunkIndex = getChunkIndexByPosition(position);
        if (chunksReceived[chunkIndex]) {
            // duplicate message was received
            return;
        }

        outputFile.seek(position);
        outputFile.write(bytes, FileProvider.CHUNK_HEADER_SIZE, payloadSize);

        chunksReceived[chunkIndex] = true;
        numberOfChunksReceived++;
    }

    boolean isDownloadComplete() {
        return numberOfChunksReceived == expectedChunks;
    }

    FileDownloadResult complete() throws IOException {
        outputFile.close();
        long endTime = currentTimeMillis();
        if (endTime == startTime) { endTime++; }
        FileDownloadResult result = new FileDownloadResult();
        result.file = file;
        result.packetsReceived = numberOfChunksReceived;
        result.expectedPackets = expectedChunks;
        // BUGBUG: need actual bytes received, not expected size
        result.kiloBitsPerSecond = startTime != 0 ? ((expectedSize * 8) / (endTime - startTime)) : 0;
        return result;
    }

    static class FileDownloadResult {
        File file;
        int packetsReceived;
        int expectedPackets;
        double packetLoss() {
            return (expectedPackets - packetsReceived) / (double) expectedPackets;
        }
        long kiloBitsPerSecond;
    }
}
