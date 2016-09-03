package com.mykwillis.udpthrottle.message;

import java.io.*;

public class InfoMessage extends Message {
    byte[] bytes;
    String filename;
    long fileSize;

    public InfoMessage(byte[] bytes) throws IOException {
        if (!isInfoMessage(bytes)) {
           throw new IllegalArgumentException("Message is not info packet");
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(inputStream);

        int packetType = dis.readInt();
        long fileSize = dis.readLong();
        int filenameSize = dis.readInt();
        byte[] filenameBytes = new byte[filenameSize];
        int filenameBytesRead = dis.read(filenameBytes);

        this.bytes = bytes;
        this.filename = new String(filenameBytes, "UTF-8");
        this.fileSize = fileSize;
    }

    public InfoMessage(String filename, long fileSize) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);

        dos.writeInt(MESSAGE_TYPE_INFO);
        dos.writeLong(fileSize);
        byte[] filenameBytes = filename.getBytes("UTF-8");
        dos.writeInt(filenameBytes.length);
        dos.write(filenameBytes);

        this.bytes = outputStream.toByteArray();
        this.filename = filename;
        this.fileSize = fileSize;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    public String getFilename() {
        return filename;
    }

    public long getFileSize() {
        return fileSize;
    }
}
