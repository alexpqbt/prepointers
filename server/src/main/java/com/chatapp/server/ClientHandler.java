package com.chatapp.server;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientHandler implements Runnable {

    private static final int MAX_MSG_LEN = 160;

    private final Socket socket;
    private final ChatServer server;
    private final UserStore userStore;
    private final Consumer<String> logger;

    private BufferedReader reader;
    private PrintWriter writer;
    private String username = null;

    public ClientHandler(Socket socket, ChatServer server,
                         UserStore userStore, Consumer<String> logger) {
        this.socket = socket;
        this.server = server;
        this.userStore = userStore;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            if (!authenticate()) return;

            server.registerClient(username, this);

            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line.trim());
            }
        } catch (IOException e) {
            if (username != null) logger.accept(username + " disconnected unexpectedly.");
        } finally {
            cleanup();
        }
    }

    private boolean authenticate() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                send("ERROR|Invalid command format.");
                continue;
            }
            String cmd = parts[0].trim();
            String user = parts[1].trim();
            String pass = parts[2].trim();

            if (cmd.equals("REGISTER")) {
                String result = userStore.register(user, pass);
                send(result);
                if (result.equals("OK")) {
                    username = user;
                    return true;
                }
            } else if (cmd.equals("LOGIN")) {
                String result = userStore.login(user, pass);
                if (result.equals("OK")) {
                    if (server.isLoggedIn(user)) {
                        send("ERROR|User already logged in.");
                    } else {
                        send("OK");
                        username = user;
                        return true;
                    }
                } else {
                    send(result);
                }
            } else {
                send("ERROR|Please login or register first.");
            }
        }
        return false;
    }

    private void handleLine(String line) {
        if (line.isEmpty()) return;
        if (!line.startsWith("CHAT|")) {
            send("ERROR|Unknown command.");
            return;
        }
        String msg = line.substring(5);
        if (msg.isBlank()) {
            send("ERROR|Message cannot be blank.");
            return;
        }
        if (msg.length() > MAX_MSG_LEN) msg = msg.substring(0, MAX_MSG_LEN);
        server.broadcast("MSG|" + username + "|" + msg);
    }

    public synchronized void send(String message) {
        if (writer != null) writer.println(message);
    }

    public void disconnect() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void cleanup() {
        if (username != null) server.removeClient(username);
        disconnect();
    }
}