package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.BeginMessage;
import com.mykwillis.udpthrottle.message.InfoMessage;
import com.mykwillis.udpthrottle.message.Message;
import com.mykwillis.udpthrottle.message.PayloadMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;

public class Client {

    /**
     * Client.jar entry point.
     *
     * @param args Command line arguments for `ipaddr` and `port`.
     */
    public static void main(String[] args) {
        String ipaddr;
        int port;

        if (args.length < 2) {
            usage();
            return;
        }

        try {
            ipaddr = args[0];
            port = Integer.parseInt(args[1]);
        } catch(NumberFormatException e) {
            System.err.println("Invalid port specified: " + e.getMessage());
            usage();
            return;
        }

        System.out.println(String.format("Client provided ip address %s port %d", ipaddr, port));

        receiveFile(ipaddr, port);
    }

    static void usage() {
        System.out.println("Usage: java -jar Client.jar <ip address> <port>");
    }

    /**
     * Send a BEGIN packet to the server, and handle the file download.
     */
    private static void receiveFile(String ipAddr, int port) {
        final int MAX_PACKET_SIZE = 65507;
        final int INACTIVITY_TIMEOUT = 5 * 1000;   // 5 seconds

        try {
            // Open the UDP socket and block, awaiting a client packet.
            DatagramSocket socket = new DatagramSocket();

            // Send BEGIN packet
            System.out.printf("Client: sending BEGIN");
            BeginMessage beginMessage = new BeginMessage();
            socket.send(beginMessage.getDatagramPacket(InetAddress.getByName(ipAddr), port));


            // Start receiving packets.
            byte[] incomingPacketBuffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket incomingPacket = new DatagramPacket(incomingPacketBuffer, incomingPacketBuffer.length);

            socket.setSoTimeout(INACTIVITY_TIMEOUT);

            FileReceiver fileReceiver = null;
            ArrayList<byte[]> pending = new ArrayList<>();
            try {
                do {
                    socket.receive(incomingPacket);
                    byte[] bytes = incomingPacket.getData();
                    int length = incomingPacket.getLength();

                    // When we receive the INFO message, we can create the FileReceiver.
                    if (Message.isInfoMessage(bytes)) {
                        InfoMessage infoMessage = new InfoMessage(bytes);
                        fileReceiver = new FileReceiver(
                                infoMessage.getFilename(), infoMessage.getFileSize());
                        System.out.printf("Client: received INFO [file: %s, length: %d]\n",
                                infoMessage.getFilename(), infoMessage.getFileSize());
                        while (!pending.isEmpty()) {
                            byte[] p = pending.remove(0);
                            fileReceiver.processPacket(p, p.length);
                        }
                        continue;
                    } else if (!Message.isPayloadMessage(bytes)) {
                        System.out.printf("Client: unexpected packet.");
                        continue;
                    }

                    PayloadMessage payloadMessage = new PayloadMessage(bytes, length);
                    System.out.printf("Client: received PAYLOAD [position: %d, length: %d]\n",
                            payloadMessage.getPosition(), payloadMessage.getPayloadSize());

                    // Normally, every PAYLOAD message would be given directly to the
                    // FileReceiver for processing as soon as it was received. However, it is
                    // possible that we have yet to receive the INFO packet, and therefore have
                    // not yet created the FileReceiver. In this case, we store the packet in-
                    // memory until the INFO packet receives.
                    if (fileReceiver == null) {
                        pending.add(Arrays.copyOfRange(bytes, payloadMessage.getHeaderSize(), length));
                    } else {
                        fileReceiver.processPacket(bytes, length);
                    }

                } while (fileReceiver == null || !fileReceiver.isDownloadComplete());

            } catch(SocketTimeoutException e) {
                System.out.printf("Timeout: " + e);
            }


            FileReceiver.FileDownloadResult result = fileReceiver.complete();
            System.out.println(String.format("Download of %s complete!", result.file.getName()));
            System.out.println("Packets received: " + result.packetsReceived);
            System.out.println("Packets expected: " + result.expectedPackets);
            System.out.println("Message Loss %: " + result.packetLoss());
            System.out.println("Average b/w (kbps): " + result.kiloBitsPerSecond);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
