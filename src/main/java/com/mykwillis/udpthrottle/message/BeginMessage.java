package com.mykwillis.udpthrottle.message;

import java.io.*;

public class BeginMessage extends Message {
    byte[] bytes;

    public BeginMessage(byte[] bytes) throws IOException {
        if (!isBeginMessage(bytes)) {
           throw new IllegalArgumentException("Message is not BEGIN packet");
        }
        this.bytes = bytes;
    }

    public BeginMessage() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);

        dos.writeInt(MESSAGE_TYPE_BEGIN);
        this.bytes = outputStream.toByteArray();
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }
}
