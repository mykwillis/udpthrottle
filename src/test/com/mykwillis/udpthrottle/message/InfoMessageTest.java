package com.mykwillis.udpthrottle.message;

import com.mykwillis.udpthrottle.message.InfoMessage;
import com.mykwillis.udpthrottle.message.Message;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static com.mykwillis.udpthrottle.message.Message.MESSAGE_TYPE_INFO;
import static org.junit.Assert.*;

public class InfoMessageTest {
    String testFilename = "TestFilename.doc";
    long testFileSize =  5683938;

    @Test
    public void createFromArgs() throws Exception {
        InfoMessage packet = new InfoMessage(testFilename, testFileSize);
        assertTrue(Message.isInfoMessage(packet.getBytes()));
        assertEquals(packet.getFilename(), testFilename);
        assertEquals(packet.getFileSize(), testFileSize);
    }

    @Test
    public void createFromBytes() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);

        dos.writeInt(MESSAGE_TYPE_INFO);
        dos.writeLong(testFileSize);
        byte[] filenameBytes = testFilename.getBytes("UTF-8");
        dos.writeInt(filenameBytes.length);
        dos.write(filenameBytes);

        byte[] testBytes = outputStream.toByteArray();

        InfoMessage packet = new InfoMessage(testBytes);

        assertTrue(Message.isInfoMessage(packet.getBytes()));
        assertArrayEquals(packet.getBytes(), testBytes);
        assertEquals(packet.getFilename(), testFilename);
        assertEquals(packet.getFileSize(), testFileSize);
    }

    @Test
    public void roundTrip() throws Exception {
        InfoMessage packet1 = new InfoMessage(testFilename, testFileSize);
        InfoMessage packet2 = new InfoMessage(packet1.getBytes());

        assertArrayEquals(packet1.getBytes(), packet2.getBytes());
        assertEquals(packet1.getFilename(), packet2.getFilename());
        assertEquals(packet1.getFileSize(), packet2.getFileSize());
    }


}