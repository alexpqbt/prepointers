package com.chatapp.server;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer — entry point.
 *
 * Responsibilities:
 *  - Load configuration from server.properties
 *  - Manage the user account CSV file
 *  - Accept incoming client connections (one thread per client)
 *  - Maintain the in-memory chat history
 *  - Broadcast messages to all connected clients
 *  - Drive the ServerGUI
 */
public class ChatServer {

    // ── Configuration ────────────────────────────────────────────────────────

    private static final String PROPERTIES_FILE = "server.properties";
    private static final String USERS_CSV        = "users.csv";

    private final String host;
    private final int    port;

    // ── State ─────────────────────────────────────────────────────────────────

    /** All currently connected client handlers. */
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /** Full chat history kept in memory for the lifetime of the server. */
    private final List<String> chatHistory = new ArrayList<>();

    /** username → password (plain text, as required by the spec). */
    private final Map<String, String> userAccounts = new LinkedHashMap<>();

    /** Usernames that are currently logged in (prevents duplicate logins). */
    private final Set<String> loggedInUsers = ConcurrentHashMap.newKeySet();

    private ServerGUI gui;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Properties props = loadProperties();
        String host = props.getProperty("host", "127.0.0.1");
        int    port = Integer.parseInt(props.getProperty("port", "5000"));

        ChatServer server = new ChatServer(host, port);
        server.start();
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    private void start() {
        // Build GUI on the Event Dispatch Thread
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui = new ServerGUI(host, port);
            gui.setVisible(true);
        });

        loadUserAccounts();

        // Accept connections in a background thread so the GUI stays responsive
        Thread acceptThread = new Thread(this::acceptLoop, "accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(host, port));
            log("Server listening on " + host + ":" + port);

            while (true) {
                Socket socket = serverSocket.accept();
                log("New connection from " + socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);

                Thread clientThread = new Thread(handler,
                        "client-" + socket.getRemoteSocketAddress());
                clientThread.setDaemon(true);
                clientThread.start();

                updateClientCount();
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        }
    }

    // ── Broadcasting ──────────────────────────────────────────────────────────

    /**
     * Appends a message to history and sends it to every connected client.
     *
     * @param message Fully-formatted chat line, e.g. "[14:02] alice: hello"
     */
    public synchronized void broadcastMessage(String message) {
        chatHistory.add(message);
        gui.appendChatLog(message);

        for (ClientHandler c : clients) {
            c.sendLine("MSG|" + message);
        }
    }

    /**
     * Replays the entire chat history to a newly authenticated client.
     */
    public synchronized void sendHistoryTo(ClientHandler handler) {
        for (String line : chatHistory) {
            handler.sendLine("MSG|" + line);
        }
    }

    // ── Client lifecycle ──────────────────────────────────────────────────────

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        if (handler.getUsername() != null) {
            loggedInUsers.remove(handler.getUsername());
            log(handler.getUsername() + " disconnected.");
        }
        updateClientCount();
    }

    private void updateClientCount() {
        if (gui != null) {
            gui.setClientCount(clients.size());
        }
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Attempts to log in with the supplied credentials.
     *
     * @return null on success, or an error message string on failure.
     */
    public synchronized String login(String username, String password) {
        if (!userAccounts.containsKey(username)) {
            return "ERROR|User not found.";
        }
        if (!userAccounts.get(username).equals(password)) {
            return "ERROR|Incorrect password.";
        }
        if (loggedInUsers.contains(username)) {
            return "ERROR|User already logged in.";
        }
        loggedInUsers.add(username);
        log(username + " logged in.");
        return null; // success
    }

    /**
     * Attempts to register a new account.
     *
     * @return null on success, or an error message string on failure.
     */
    public synchronized String register(String username, String password) {
        if (username.isBlank() || password.isBlank()) {
            return "ERROR|Username and password cannot be empty.";
        }
        if (password.length() < 4) {
            return "ERROR|Password must be at least 4 characters.";
        }
        if (userAccounts.containsKey(username)) {
            return "ERROR|Username already taken.";
        }
        userAccounts.put(username, password);
        saveUserAccounts();
        log("Registered new user: " + username);
        return null; // success
    }

    // ── CSV persistence ───────────────────────────────────────────────────────

    private void loadUserAccounts() {
        Path csvPath = Path.of(USERS_CSV);

        if (!Files.exists(csvPath)) {
            log("No users.csv found — creating an empty one.");
            try {
                Files.writeString(csvPath, "username,password\n");
            } catch (IOException e) {
                log("Could not create users.csv: " + e.getMessage());
            }
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; } // skip header row
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    userAccounts.put(parts[0].trim(), parts[1].trim());
                }
            }
            log("Loaded " + userAccounts.size() + " user account(s).");
        } catch (IOException e) {
            log("Failed to load users.csv: " + e.getMessage());
        }
    }

    private void saveUserAccounts() {
        Path csvPath = Path.of(USERS_CSV);
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            writer.write("username,password\n");
            for (Map.Entry<String, String> entry : userAccounts.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            log("Failed to save users.csv: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = ChatServer.class
                .getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Could not load " + PROPERTIES_FILE
                    + " — using defaults.");
        }
        return props;
    }

    public void log(String message) {
        String ts  = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + ts + "] " + message;
        System.out.println(line);
        if (gui != null) {
            gui.appendConsoleLog(line);
        }
    }
}