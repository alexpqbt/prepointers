package com.chatapp.server;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ClientHandler — one instance per connected client socket.
 *
 * Runs on its own thread. Reads newline-delimited commands from the client,
 * delegates authentication to ChatServer, and handles CHAT messages.
 *
 * Protocol commands (client → server):
 *   LOGIN|username|password
 *   REGISTER|username|password
 *   CHAT|message text
 *
 * Protocol responses (server → client):
 *   OK            — authentication success
 *   ERROR|reason  — failure
 *   MSG|line      — a chat message line (history replay or live broadcast)
 */
public class ClientHandler implements Runnable {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final Socket     socket;
    private final ChatServer server;

    private BufferedReader reader;
    private PrintWriter    writer;

    /** Set after successful login/register. Null until authenticated. */
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"),
                    true /* auto-flush */);

            handleAuthentication();

            if (username != null) {
                // Replay history before entering live chat loop
                server.sendHistoryTo(this);
                handleChat();
            }

        } catch (IOException e) {
            server.log("Connection error for "
                    + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        } finally {
            server.removeClient(this);
            close();
        }
    }

    // ── Authentication phase ──────────────────────────────────────────────────

    /**
     * Keeps reading commands until LOGIN or REGISTER succeeds, then returns.
     */
    private void handleAuthentication() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\|", 3);
            String cmd = parts[0];

            switch (cmd) {
                case "LOGIN" -> {
                    if (parts.length < 3) { sendLine("ERROR|Malformed LOGIN."); break; }
                    String error = server.login(parts[1], parts[2]);
                    if (error == null) {
                        username = parts[1];
                        sendLine("OK");
                        return;
                    } else {
                        sendLine(error);
                    }
                }
                case "REGISTER" -> {
                    if (parts.length < 3) { sendLine("ERROR|Malformed REGISTER."); break; }
                    String error = server.register(parts[1], parts[2]);
                    if (error == null) {
                        // Auto-login after registration
                        server.login(parts[1], parts[2]);
                        username = parts[1];
                        sendLine("OK");
                        return;
                    } else {
                        sendLine(error);
                    }
                }
                default -> sendLine("ERROR|Unknown command.");
            }
        }
    }

    // ── Chat phase ────────────────────────────────────────────────────────────

    /**
     * Reads CHAT commands and broadcasts them until the connection closes.
     */
    private void handleChat() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("CHAT|")) {
                String text = line.substring(5).trim();
                if (text.isEmpty()) continue;

                String timestamp = LocalDateTime.now().format(TIME_FMT);
                String formatted = "[" + timestamp + "] " + username + ": " + text;
                server.broadcastMessage(formatted);
            }
        }
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    /** Sends a single newline-terminated line to this client. */
    public void sendLine(String line) {
        if (writer != null && !socket.isClosed()) {
            writer.println(line);
        }
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getUsername() { return username; }
}