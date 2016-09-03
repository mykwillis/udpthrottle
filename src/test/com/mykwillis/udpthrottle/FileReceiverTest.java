package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class FileReceiverTest {
    static final int TEST_FILE_SIZE = 2001;
    static final int RANDOM_SEED = 12341234;
    FileReceiver fileReceiver;
    Random random = new Random(RANDOM_SEED);

    @Before
    public void createDownloadedFile() throws IOException {
        fileReceiver = new FileReceiver("test.download", TEST_FILE_SIZE);
    }

    @After
    public void deleteDownloadedFile() throws IOException {
        FileReceiver.FileDownloadResult result = fileReceiver.complete();
        result.file.delete();
    }

    @Test
    public void getExpectedChunks() throws Exception {
        int actual;
        int expected;
        int bytes;

        bytes = 0;
        expected = 0;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);

        bytes = FileProvider.CHUNK_PAYLOAD_SIZE-1;
        expected = 1;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);

        bytes = FileProvider.CHUNK_PAYLOAD_SIZE;
        expected = 1;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);

        bytes = FileProvider.CHUNK_PAYLOAD_SIZE + 1;
        expected = 2;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);

        bytes = 2 * (FileProvider.CHUNK_PAYLOAD_SIZE - 1);
        expected = 2;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);

        bytes = 42 * (FileProvider.CHUNK_PAYLOAD_SIZE);
        expected = 42;
        actual = FileReceiver.getExpectedChunks(bytes);
        assertEquals(expected, actual);
    }

    @Test
    public void getChunkIndexByPosition() throws Exception {
        long position;
        int expected;
        int actual;

        position = 0;
        assertEquals(0, FileReceiver.getChunkIndexByPosition(position));

        position = 1;
        assertEquals(0, FileReceiver.getChunkIndexByPosition(position));

        position = FileProvider.CHUNK_PAYLOAD_SIZE - 1;
        assertEquals(0, FileReceiver.getChunkIndexByPosition(position));

        position = FileProvider.CHUNK_PAYLOAD_SIZE;
        assertEquals(1, FileReceiver.getChunkIndexByPosition(position));

        position = 3 * FileProvider.CHUNK_PAYLOAD_SIZE - 1;
        assertEquals(2, FileReceiver.getChunkIndexByPosition(position));

        position = 3 * FileProvider.CHUNK_PAYLOAD_SIZE;
        assertEquals(3, FileReceiver.getChunkIndexByPosition(position));

        position = 3 * FileProvider.CHUNK_PAYLOAD_SIZE + 1;
        assertEquals(3, FileReceiver.getChunkIndexByPosition(position));

        position = 42 * FileProvider.CHUNK_PAYLOAD_SIZE - 1;
        assertEquals(41, FileReceiver.getChunkIndexByPosition(position));

        position = 42 * FileProvider.CHUNK_PAYLOAD_SIZE + 1;
        assertEquals(42, FileReceiver.getChunkIndexByPosition(position));
    }

    @Test
    public void processPacket_smallFile() throws Exception {
        fileReceiver = new FileReceiver("test2.download", 1000);

        // build random chunk of file data
        byte[] chunk = new byte[1000 + FileProvider.CHUNK_HEADER_SIZE];
        random.nextBytes(chunk);
        ByteBuffer byteBuffer = ByteBuffer.wrap(chunk);
        byteBuffer.putInt(Message.MESSAGE_TYPE_PAYLOAD);
        byteBuffer.putLong(0);

        // verify processPacket takes 1000 bytes at position 0
        fileReceiver.processPacket(chunk, chunk.length);

        FileReceiver.FileDownloadResult result = fileReceiver.complete();
        byte[] expectedFileBytes = Arrays.copyOfRange(chunk, FileProvider.CHUNK_HEADER_SIZE, chunk.length);
        byte[] actualFileBytes = Files.readAllBytes(result.file.toPath());
        assertArrayEquals(expectedFileBytes, actualFileBytes);

        assertEquals(result.packetsReceived, 1);
    }

    @Test
    public void processPacket_middleChunk() throws Exception {

        // Send the 4th chunk of a file, and verify it was written properly.

        int fileSize = FileProvider.CHUNK_PAYLOAD_SIZE * 6 + 1234;
        fileReceiver = new FileReceiver("test3.download", fileSize);

        // build random middle chunk
        // chunk of file data
        byte[] chunk = new byte[FileProvider.CHUNK_SIZE];
        random.nextBytes(chunk);
        int position = 3 * FileProvider.CHUNK_PAYLOAD_SIZE;   // beginning of fourth chunk
        ByteBuffer byteBuffer = ByteBuffer.wrap(chunk);
        byteBuffer.putInt(Message.MESSAGE_TYPE_PAYLOAD);
        byteBuffer.putLong(position);

        fileReceiver.processPacket(chunk, chunk.length);

        FileReceiver.FileDownloadResult result = fileReceiver.complete();
        byte[] expectedPayloadBytes = Arrays.copyOfRange(chunk, FileProvider.CHUNK_HEADER_SIZE, chunk.length);
        byte[] allFileBytes = Files.readAllBytes(result.file.toPath());
        byte[] actualPayloadBytes = Arrays.copyOfRange(allFileBytes, position, position + FileProvider.CHUNK_PAYLOAD_SIZE);
        assertArrayEquals(expectedPayloadBytes, actualPayloadBytes);

        assertEquals(result.packetsReceived, 1);
    }

    @Test
    public void readPacket_throwsOnInvalidPosition() throws Exception {

    }


    @Test
    public void createsProperOutputFileSize() throws Exception {
        FileReceiver.FileDownloadResult result = fileReceiver.complete();
        assertEquals(TEST_FILE_SIZE, result.file.length());
    }

}