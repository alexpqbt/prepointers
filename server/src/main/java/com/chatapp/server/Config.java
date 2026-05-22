package com.chatapp.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public final String host;
    public final int port;

    public Config() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/server.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load server.properties, using defaults.");
        }
        host = props.getProperty("host", "127.0.0.1");
        port = Integer.parseInt(props.getProperty("port", "5000"));
    }
}