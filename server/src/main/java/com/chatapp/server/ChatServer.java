package com.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatServer {

    private final Config config;
    private final UserStore userStore;
    private final Consumer<String> logger;
    private final Consumer<Integer> onClientCountChanged;
    private final Consumer<String> onBroadcast;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    public ChatServer(Config config, UserStore userStore,
                      Consumer<String> logger, Consumer<Integer> onClientCountChanged,
                      Consumer<String> onBroadcast) {
        this.config = config;
        this.userStore = userStore;
        this.logger = logger;
        this.onClientCountChanged = onClientCountChanged;
        this.onBroadcast = onBroadcast;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.host, config.port));
        running = true;
        logger.accept("Server started on " + config.host + ":" + config.port);

        Thread acceptThread = new Thread(this::acceptLoop, "accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                logger.accept("New connection from " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this, userStore, logger);
                Thread t = new Thread(handler, "client-" + socket.getPort());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) logger.accept("Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientHandler h : connectedClients.values()) h.disconnect();
        connectedClients.clear();
        logger.accept("Server stopped.");
        onClientCountChanged.accept(0);
    }

    public void registerClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
        onClientCountChanged.accept(connectedClients.size());
        broadcast("SYSTEM|" + username + " joined the chat.");
        logger.accept(username + " joined. Clients: " + connectedClients.size());
    }

    public void removeClient(String username) {
        connectedClients.remove(username);
        onClientCountChanged.accept(connectedClients.size());
        broadcast("SYSTEM|" + username + " left the chat.");
        logger.accept(username + " left. Clients: " + connectedClients.size());
    }

    public boolean isLoggedIn(String username) {
        return connectedClients.containsKey(username);
    }

    public void broadcast(String message) {
        onBroadcast.accept(message);
        for (ClientHandler h : connectedClients.values()) {
            h.send(message);
        }
    }
}