package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.Message;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

public class FileProviderTest {
    @Test
    public void getChunk_singleChunkFile() throws Exception {
        File file = new File("src/test/resources/test-file-small.txt");
        FileProvider fileProvider = new FileProvider(file);

        assertNotNull(fileProvider.getChunk(0));
        assertNull(fileProvider.getChunk(1));
        assertNull(fileProvider.getChunk(2));
        assertNull(fileProvider.getChunk(3));
    }

    int getExpectedChunks(int bytes) {
        // Round up to include last chunk to handle remainder
        return (bytes + FileProvider.CHUNK_PAYLOAD_SIZE - 1) / FileProvider.CHUNK_PAYLOAD_SIZE;
    }

    @Test
    public void getChunk_multipleChunkFile() throws Exception {
        File file = new File("src/test/resources/test-file-medium.txt");
        FileProvider fileProvider = new FileProvider(file);

        if (file.length() <= FileProvider.CHUNK_PAYLOAD_SIZE) {
            throw new Exception("Invalid test: Test file must be larger than CHUNK_PAYLOAD_SIZE");
        }

        int expectedChunks = getExpectedChunks((int) file.length());

        for(int i=0;i<expectedChunks;i++) {
            assertNotNull(fileProvider.getChunk(i));
        }
        assertNull(fileProvider.getChunk(expectedChunks));
    }

    @Test
    public void readFile_singleChunkFile() throws Exception {
        File file = new File("src/test/resources/test-file-small.txt");
        FileProvider fileProvider = new FileProvider(file);

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        if (fileBytes.length >= FileProvider.CHUNK_PAYLOAD_SIZE) {
            throw new Exception("Invalid test: Test file must be smaller than CHUNK_PAYLOAD_SIZE");
        }

        byte[] chunk = fileProvider.getChunk(0);

        ByteBuffer byteBuffer = ByteBuffer.wrap(chunk);
        assertEquals(FileProvider.CHUNK_HEADER_SIZE + fileBytes.length, chunk.length);
        assertEquals(Message.MESSAGE_TYPE_PAYLOAD, byteBuffer.getInt());
        assertEquals(byteBuffer.getLong(), 0);   // offset should be zero

        byte[] chunkPayload = Arrays.copyOfRange(chunk, FileProvider.CHUNK_HEADER_SIZE, chunk.length);   // end index is exclusive
        assertArrayEquals(fileBytes, chunkPayload);
    }


    @Test
    public void readFile_multipleChunkFile() throws Exception {
        File file = new File("src/test/resources/test-file-medium.txt");
        FileProvider fileProvider = new FileProvider(file);

        byte[] fileBytes = Files.readAllBytes(file.toPath());

        int expectedChunks = getExpectedChunks(fileBytes.length);
        if (expectedChunks < 2) {
            throw new Exception("Invalid test: Test file must be larger than CHUNK_PAYLOAD_SIZE");
        }

        // The last chunk may be partial if the file size is not an equal multiple
        // of CHUNK_SIZE.
        int remainder = fileBytes.length % FileProvider.CHUNK_PAYLOAD_SIZE;

        int expectedLastChunkSize = remainder > 0 ?
                FileProvider.CHUNK_HEADER_SIZE + remainder : FileProvider.CHUNK_SIZE;

        assertEquals(expectedChunks, fileProvider.chunks.size());

        int expectedChunkSize = FileProvider.CHUNK_SIZE;
        int expectedPosition = 0;
        for(int i=0;i<expectedChunks;i++) {
            byte[] chunk = fileProvider.getChunk(i);
            if (i == expectedChunks-1) {
                expectedChunkSize = expectedLastChunkSize;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(chunk);
            assertEquals(expectedChunkSize, chunk.length);  // validate length
            assertEquals(Message.MESSAGE_TYPE_PAYLOAD, byteBuffer.getInt());
            assertEquals(expectedPosition, byteBuffer.getLong()); // validate offset (header)
            byte[] chunkPayload = Arrays.copyOfRange(chunk, FileProvider.CHUNK_HEADER_SIZE, chunk.length);
            for(int pos=0;pos<chunkPayload.length;pos++) {
                if (chunkPayload[pos] != fileBytes[expectedPosition+pos]) {
                    throw new Exception("Payload invalid at file position: " + expectedPosition+pos);
                }
            }
            expectedPosition += (chunk.length - FileProvider.CHUNK_HEADER_SIZE);
        }

    }
}