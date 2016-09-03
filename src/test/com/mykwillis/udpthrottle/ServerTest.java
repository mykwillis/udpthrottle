package com.mykwillis.udpthrottle;

import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

public class ServerTest {
    private final ByteArrayOutputStream testOut = new ByteArrayOutputStream();

    @Before
    public void setupStreams() {
        System.setOut(new PrintStream(testOut));
    }

    @org.junit.Test
    public void main_failsOnBadInput() throws Exception {
        // no arguments
        Server.main(new String[0]);
        assertThat(testOut.toString(), containsString("Usage"));

        // invalid port argument
        Server.main(new String[]{"not_an_int", "filename"});
        assertThat(testOut.toString(), containsString("Usage"));

        // missing filename
        Server.main(new String[]{"3000"});
        assertThat(testOut.toString(), containsString("Usage"));
    }
}