package com.mykwillis.udpthrottle.message;

import com.mykwillis.udpthrottle.FileProvider;

import java.io.*;

public class PayloadMessage extends Message {
    byte[] bytes;
    long position;
    int length;

    /**
     * Create PayloadMessage for buffer read from network.
     *
     * @param bytes byte array contining bytes read from network. The length of this
     *              array may be greater than the actual bytes read; parameter `length`
     *              determines how many bytes in the array should be considered valid.
     * @param length the number of bytes in `bytes` that were read from the network.
     * @throws IOException
     */
    public PayloadMessage(byte[] bytes, int length) throws IOException {
        if (!isPayloadMessage(bytes)) {
           throw new IllegalArgumentException("Message is not payload packet");
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(inputStream);

        int packetType = dis.readInt();
        long position = dis.readLong();

        this.bytes = bytes;
        this.position = position;
        this.length = length;
    }

    public PayloadMessage(FileProvider fileProvider, int chunkIndex) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);

        byte[] chunk = fileProvider.getChunk(chunkIndex);
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk does not exist");
        }
        dos.writeInt(MESSAGE_TYPE_PAYLOAD);
        dos.writeLong(chunk.length);

        this.bytes = outputStream.toByteArray();
        this.position = chunkIndex * FileProvider.CHUNK_PAYLOAD_SIZE;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    public int getHeaderSize() {
        return 12;  // 4 for int packetType, 8 for long length
    }

    public long getPosition() {
        return position;
    }

    public long getPayloadSize() {
        return length - getHeaderSize();
    }
}
