package com.mykwillis.udpthrottle;

import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;


public class ClientTest {
    private final ByteArrayOutputStream testOut = new ByteArrayOutputStream();

    @Before
    public void setupStreams() {
        System.setOut(new PrintStream(testOut));
    }

    @org.junit.Test
    public void main_failsOnBadInput() throws Exception {
        // no arguments
        Client.main(new String[0]);
        assertThat(testOut.toString(), containsString("Usage"));

        // invalid port argument
        Client.main(new String[]{"10.1.1.1", "not_a_port_number"});
        assertThat(testOut.toString(), containsString("Usage"));

        // missing port
        Client.main(new String[]{"10.1.1.1"});
        assertThat(testOut.toString(), containsString("Usage"));
    }
}