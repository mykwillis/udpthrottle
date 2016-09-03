package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.InfoMessage;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Throttled UDP Server.
 *
 */
public class Server {
    final static int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 Mebibytes
    private int serverPort;
    private File file;
    private BlockingQueue<ClientContext> clients = new LinkedBlockingQueue<>();

    /**
     * Server.jar entry point.
     *
     * @param args Command line arguments for `port` and `filename`
     */
    public static void main(String[] args) {
        int port;
        String filename;

        if (args.length < 2) {
            usage();
            return;
        }

        try {
            port = Integer.parseInt(args[0]);
            filename = args[1].replaceAll("^\"|\"$", "");   // strip quote (") symbols from beginning and end
        } catch(NumberFormatException e) {
            System.err.println("Failed to parse port: " + e.getMessage());
            usage();
            return;
        }

        System.out.println(String.format("Server provided port %d and file %s", port, filename));

        File file = new File(filename);
        if (!file.exists() || file.isDirectory()) {
            System.err.println("The file " + filename + " does not exist, or is a directory.");
            usage();
            return;
        }

        new Server(port, file).run();
    }

    static void log(String msg) {
        System.err.println(msg);
    }

    static void usage() {
        System.out.println("Usage: java -jar Server.jar <port> <filename>");
    }

    public Server(int port, File file) {
        this.serverPort = port;
        this.file = file;
    }


    /**
     * Begin listening on our socket, and start the sender thread.
     *
     * This method serves as the main loop for receiving BEGIN messages from the client.
     * Each time a BEGIN message is received, it sends an INFO message and places a ClientContext
     * object on the queue it shares with the SenderThread.
     *
     * The SenderThread, responsible for sending PAYLOAD messages to the client, is created
     * by this method.
     */
    public void run() {

        try {
            if (file.length() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File is too large.");
            }
            FileProvider fileProvider = new FileProvider(file);

            System.out.printf("Server: Serving file [name=%s, size=%d]\n",
                    file.getName(), file.length());
            System.out.println("Target bandwidth is " + (ThrottleSocket.TARGET_BITS_PER_SECOND / 1000) + " kbps");

            ThrottleSocket socket = new ThrottleSocket(serverPort);

            new Thread(new SenderThread(socket, fileProvider, clients)).start();

            do {
                DatagramPacket incomingPacket = socket.receive();
                InetAddress addr = incomingPacket.getAddress();
                int port = incomingPacket.getPort();

                System.out.printf("Server: received BEGIN [client=%s:%d]\n", addr
                        .getHostAddress(), port);

                // Send info packet.
                InfoMessage infoMessage = new InfoMessage(file.getName(), file.length());
                socket.send(infoMessage, addr, port);


                // Add the client to the queue of clients.
                ClientContext context = new ClientContext();
                context.addr = addr;
                context.port = port;
                clients.add(context);

            } while(true);


        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends file data to connected clients.
     *
     * There is a single sender thread that is used for sending PAYLOAD messages to all
     * clients. This thread loops through the collection of active clients, sending
     * each client messages in a round-robin fashion. It uses a ThrottleSocket object
     * to enforce global bandwidth limitations.
     */
    private class SenderThread implements Runnable {
        private final ThrottleSocket socket;
        private final FileProvider fileProvider;
        private final BlockingQueue<ClientContext> clients;

        SenderThread(ThrottleSocket socket, FileProvider fileProvider,
                     BlockingQueue<ClientContext> clients) {
            this.socket = socket;
            this.fileProvider = fileProvider;
            this.clients = clients;
        }

        /**
         * Entry point for sender thread.
         *
         * This thread will loop forever (or until interrupted), sending messages to clients
         * when they are waiting, and blocking until new clients are connected.
         */
        @Override
        public void run() {

            // Loop until InterruptedException forces a return
            while (true) {
                // Iterate the connected clients, sending each one a single PAYLOAD message
                // in round-robin fashion.
                for (ClientContext client : clients) {
                    byte[] chunk = fileProvider.getChunk(client.nextChunk);
                    if (chunk == null) {
                        clients.remove(client);
                        System.out.printf("Server: sent all chunks [client=%s:%d]\n",
                                client.addr.getHostAddress(), client.port);
                        continue;
                    }

                    System.out.printf("Server: sending chunk %d, length %d [client=%s:%d]\n",
                            client.nextChunk, chunk.length, client.addr.getHostAddress(), client.port);

                    try {
                        socket.send(chunk, client.addr, client.port);
                        client.nextChunk++;
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // If there are no clients in the queue, we need to block until one is added.
                // (In the case where there are clients in the queue, we will immediately go
                // back to the beginning of the send loop).
                if (clients.isEmpty()) {
                    // There is no blocking peek(), so do a (blocking) take() and replace the
                    // item that was removed with add().
                    ClientContext client = null;
                    try {
                        client = clients.take();
                    } catch (InterruptedException e) {
                        System.err.println("SenderThread was interrupted.");
                        return;
                    }
                    clients.add(client);
                }
            }
        }
    }

    /**
     * Represents a client that has requested the file download.
     */
    class ClientContext {
        InetAddress addr;
        int port;
        int nextChunk = 0;  // next file chunk to be sent
    }
}
