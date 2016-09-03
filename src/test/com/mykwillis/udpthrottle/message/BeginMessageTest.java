package com.mykwillis.udpthrottle.message;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static com.mykwillis.udpthrottle.message.Message.MESSAGE_TYPE_BEGIN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class BeginMessageTest {

    @Test
    public void create() throws Exception {
        BeginMessage packet = new BeginMessage();
        assertTrue(Message.isBeginMessage(packet.getBytes()));
    }

    @Test
    public void createFromBytes() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);

        dos.writeInt(MESSAGE_TYPE_BEGIN);

        byte[] testBytes = outputStream.toByteArray();

        BeginMessage packet = new BeginMessage(testBytes);

        assertTrue(Message.isBeginMessage(packet.getBytes()));
        assertArrayEquals(packet.getBytes(), testBytes);
    }

    @Test
    public void roundTrip() throws Exception {
        BeginMessage packet1 = new BeginMessage();
        BeginMessage packet2 = new BeginMessage(packet1.getBytes());

        assertArrayEquals(packet1.getBytes(), packet2.getBytes());
    }
}