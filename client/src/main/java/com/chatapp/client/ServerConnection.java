package com.chatapp.client;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {

    private final Config config;
    private final Consumer<String> onMessage;
    private final Consumer<String> onStatus;

    private Socket socket;
    private PrintWriter writer;
    private volatile boolean connected = false;

    public ServerConnection(Config config, Consumer<String> onMessage, Consumer<String> onStatus) {
        this.config = config;
        this.onMessage = onMessage;
        this.onStatus = onStatus;
    }

    public boolean connect() {
        try {
            socket = new Socket(config.serverHost, config.serverPort);
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            connected = true;

            Thread reader = new Thread(this::readLoop, "read-thread");
            reader.setDaemon(true);
            reader.start();

            onStatus.accept("Connected to server.");
            return true;
        } catch (IOException e) {
            onStatus.accept("Could not connect: " + e.getMessage());
            return false;
        }
    }

    private void readLoop() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                onMessage.accept(line);
            }
        } catch (IOException e) {
            if (connected) onStatus.accept("Disconnected from server.");
        } finally {
            connected = false;
            close();
        }
    }

    public void send(String message) {
        if (writer != null && connected) writer.println(message);
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}