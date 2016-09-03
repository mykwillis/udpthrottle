package com.mykwillis.udpthrottle;

import com.mykwillis.udpthrottle.message.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import static java.lang.System.currentTimeMillis;

/**
 * Socket wrapper that enforces a maximum bandwidth limitation.
 *
 * Our general strategy is to measure the actual bytes sent over some (small)
 * period of time, and then introduce an artificial delay via sleep() when that
 * rate exceeds our target value.
 *
 * There are a couple of heuristics employed that attempt to smooth out the send
 * rate, preventing it from spiking for small periods of time before being stopped
 * completely.
 */
class ThrottleSocket {

    /**
     * Desired bandwidth utilization, in bits per second.
     */
    static final int TARGET_BITS_PER_SECOND = 1 * 1024 * 1024;         // 1 Mebibit

    /**
     * Convenience constant for internal calculations that use bytes per millisecond.
     */
    final static int TARGET_BYTES_PER_MILLISECOND = (TARGET_BITS_PER_SECOND / 8) / 1000;

    /**
     * Number of bytes that will be sent before checking bandwidth utilization. This
     * is chosen to be ~ 10 times / second when we are saturating allocated bandwidth.
     */
    final static int BYTES_BETWEEN_DELAY_CHECKS = TARGET_BYTES_PER_MILLISECOND * 100;

    private DatagramSocket socket;
    private long start = 0;
    private long bytesSentThisPeriod = 0;
    private boolean shouldStallEachPacket = true;

    public ThrottleSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    ThrottleSocket(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
    }

    void send(Message message, InetAddress addr, int port) throws IOException, InterruptedException {
        DatagramPacket packet = message.getDatagramPacket(addr, port);
        send(packet);
    }

    void send(byte[] bytes, InetAddress addr, int port) throws IOException, InterruptedException {
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, addr, port);
        send(packet);
    }

    /**
     * Send a datagram over the network, blocking if necessary to enforce bandwidth limits.
     *
     * Bandwidth limits are enforced by periodically checking the
     */
    private void send(DatagramPacket packet) throws IOException, InterruptedException {

        if (start == 0) {
            start = currentTimeMillis();
        }

        // In normal conditions, we are capable of sending bursts of data down to the network
        // at a much faster rate than would be allowable by our bandwidth limits. When this is
        // true, we introduce a delay before every packet to smooth out the utilization and
        // avoid excessive burstiness.
        if (shouldStallEachPacket) {
            Thread.sleep(1);
        }

        socket.send(packet);
        bytesSentThisPeriod += packet.getLength();

        if (bytesSentThisPeriod >= BYTES_BETWEEN_DELAY_CHECKS) {
            long end = currentTimeMillis();
            long thisPeriodMs = end - start;
            System.out.println("Elapsed time: " + thisPeriodMs + "ms");

            // The clock resolution may be as bad as 10's of milliseconds, and so it is
            // possible that (end-start) will be zero.  Increment in this case to avoid
            // unpleasantries with division by zero.
            if (thisPeriodMs == 0) {
                thisPeriodMs++;
            }

            // Determine how long we should sleep in order to get us back to our target
            // bandwidth. This formula is derived from the fact that:
            //
            // Bandwidth = (bytes sent) / time
            //           = (bytes sent) / (time sending + time sleeping)
            //
            long sleepTime = (bytesSentThisPeriod / TARGET_BYTES_PER_MILLISECOND) - thisPeriodMs;

            // Heuristic: if we are running more than twice as fast as we should, start introducing
            // a delay between every packet (in addition to sleeping to make up for the fact
            // that we've gone too quickly). We do this because our timer resolution is such that
            // there is only so often we can actually measure.
            shouldStallEachPacket = (sleepTime > thisPeriodMs);

            if (sleepTime > 0) {
                System.out.println("Server: sleeping for " + sleepTime + " ms");
                Thread.sleep(sleepTime);
            }

            long periodEnd = currentTimeMillis();
            long periodBandwidth = ((bytesSentThisPeriod * 8) / (periodEnd - start)); // kilobits per second
            System.out.println("Server: b/w used this period: " + periodBandwidth + " kbps");
            start = periodEnd;
            bytesSentThisPeriod = 0;
        }

    }


    DatagramPacket receive() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        do {
            socket.receive(packet);
        } while (!Message.isBeginMessage(packet.getData()));

        return packet;
    }
}
