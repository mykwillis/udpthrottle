package com.mykwillis.udpthrottle.message;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;

public abstract class Message {
    public static final int MESSAGE_TYPE_INVALID = 0xFF;
    public static final int MESSAGE_TYPE_BEGIN = 0x01;
    public static final int MESSAGE_TYPE_INFO = 0x02;
    public static final int MESSAGE_TYPE_PAYLOAD = 0x03;

    public static boolean isBeginMessage(byte[] bytes) {
        return isMessageType(MESSAGE_TYPE_BEGIN, bytes);
    }

    public static boolean isInfoMessage(byte[] bytes) {
        return isMessageType(MESSAGE_TYPE_INFO, bytes);
    }

    public static boolean isPayloadMessage(byte[] bytes) {
        return isMessageType(MESSAGE_TYPE_PAYLOAD, bytes);
    }

    static boolean isMessageType(int packetType, byte[] bytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(inputStream);
        try {
            return dis.readInt() == packetType;
        } catch(IOException e) {
            return false;
        }
    }

    public abstract byte[] getBytes();

    public DatagramPacket getDatagramPacket() {
        byte[] bytes = getBytes();
        return new DatagramPacket(bytes, bytes.length);
    }

    public DatagramPacket getDatagramPacket(InetAddress ipAddr, int port) {
        byte[] bytes = getBytes();
        return new DatagramPacket(bytes, bytes.length, ipAddr, port);
    }
}
