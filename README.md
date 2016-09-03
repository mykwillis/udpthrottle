UDP-based File Transfer
=======================

This project implements a UDP-based protocol for transferring a source
file from a Server to any number of remote Client machines while 
limiting bandwidth.

The project is structured as a single Java package which contains
all classes used to implement both the Client and Server components.
Two different .JAR files are built from the same package, each of
which specifies a different entry point (one for Client, one for 
Server).


Protocol Details
================

 Client -------[BEGIN] ----------> Server
 
 Client <------[INFO] ------------ Server
 
 Client <------[PAYLOAD] --------- Server
 
 Client <------[PAYLOAD] --------- Server
 
 Client <------[...] ------------- Server

 
To initiate a download, a Client sends a BEGIN message to the
Server. The Server responds by sending an INFO message to the client,
indicating the filename and total size of the file that will be sent. 
The Server then sends the Client a series of zero or more PAYLOAD 
messages, each containing a portion of the file.


Message Formats
===============

Messages are sent as UDP datagrams. Each message begins with a 4-byte 
integer that indicates its type (one of BEGIN, INFO, or PAYLOAD). 
Message length is not given explicitly, but can be determined from the 
UDP datagram.

BEGIN message 
    INT packetType      ; Always "1"

INFO message
    INT packetType      ; Always "2"
    LONG fileSize       ; Size of file, in bytes
    INT filenameSize    ; Size of filename that follows, in bytes
    STRING filename     ; UTF-8 encoded name of file being served
    
PAYLOAD message
    INT packetType      ; Always "3" 
    LONG offset         ; File offset of this message's payload data
    BYTE[] payload      ; variable-length payload
    

Strings that appear in message headers are encoded with the UTF-8
character set. Integer values are either 4-byte (int) or 8-byte (long)
signed. All fields are in network (big-endian) order.


Threading
=========

The Client is a single-threaded application that performs all work
on its main thread. It performs blocking network operations on this
thread, though it uses a socket timeout value of ~5 seconds to detect
a broken connection to the Server.

The Server is a multi-threaded application that makes use of two 
dedicated threads. The first, the server thread, blocks indefinitely 
waiting for BEGIN messages to arrive from Clients. It handles these
requests by replying with an INFO message, and then adding information
about the client to a collection it shares with the sender thread.
The (single) sender thread loops through all clients, sending PAYLOAD
messages to each in round-robin. It operates continuously, sleeping
when necessary to enforce bandwidth limits, or when waiting for a
client to make a request.


Limiting Bandwidth
==================

We limit the bandwidth used by the application by use of the 
ThrottleSocket class, a wrapper over the standard datagram socket's
send() API that tracks how much data is transmitted over a period of
time, and determines when and for how long it should block callers
in order to avoid exceeding the target bandwidth utilization.

This general strategy of ThrottleSocket (track b/w used over short
periods of time, and introduce artificial delays when that exceeds
the target rate) has a few nuances and heuristics that should be tuned
for the intended environment. The limited precision and resolution 
of Java timers also introduces some difficulties.

The specific implementation of ThrottleSocket seems to behave well with
several connected clients, b/w limits around 1 Mbps, and 8k PAYLOAD 
packets. Different parameters may require different tuning.


(Un)Reliability
===============

All messages are sent as UDP datagrams, and may therefore be lost, 
duplicated, or delivered out of order[1].

The loss of any BEGIN or INFO packet will cause the file transfer to 
fail. No attempt is made to retry transmission of these messages.

Lost PAYLOAD messages cause an incomplete download; the Client will 
create the destination file, with the proper size as given by the
Server, while noting the packet loss to the console. The byte range(s) 
of the destination file corresponding to data from lost messages 
will be left undefined.

The re-ordering of PAYLOAD messages is handled gracefully by the Client,
which makes use of the `offset` field of the PAYLOAD header to copy
each message's data to the appropriate destination file byte range.

Because the protocol dictates that the server begin sending PAYLOAD 
messages immediately after having sent an INFO message, it is possible
that one or more PAYLOAD messages may arrive before the INFO message.
The Client handles this situation by storing PAYLOAD messages in memory
until it has received the INFO message, at which point it writes all
stored PAYLOAD data to the destination file and begins processing 
future PAYLOAD messages normally.

Duplicate BEGIN messages will result in a client receiving the same
file twice.

Duplicate INFO messages are handled by ignoring all but the first
received by the Client.

Duplicate PAYLOAD messages are handled by ignoring all but the first
received by the Client for any given file offset.

[1] We assume that UDP checksums are being calculated and verified, such
that the integrity of messages is thereby sufficiently assured.

Notes / Assumptions
===================
The specification for this project indicates that "The transmission
[of the file] needs to be 10Mbps (Megabyte per second)". 
    
Because bandwidth is normally measured in units of bps ("bits 
per second") rather than Bps ("bytes per second"), we will assume
that the intended bandwidth utilization is actually 10 Mbps = 10 
Megabits per second, and that the "(Megabyte per second)" in the
specification was in error.


License
=======
Copyright 2016 Myk Willis & Company, LLC.

