package com.chatapp.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public final String serverHost;
    public final int serverPort;

    public Config() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/client.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load client.properties, using defaults.");
        }
        serverHost = props.getProperty("server.host", "127.0.0.1");
        serverPort = Integer.parseInt(props.getProperty("server.port", "5000"));
    }
}