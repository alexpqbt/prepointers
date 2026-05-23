package com.chatapp.client;

import java.io.*;
import java.net.Socket;
import java.util.Properties;

/**
 * ChatClient — entry point and networking layer.
 *
 * Owns the socket connection to the server. Provides methods for the GUI to:
 *   - Connect / disconnect
 *   - Send LOGIN, REGISTER, CHAT commands
 *
 * Reads incoming lines from the server on a background thread and forwards
 * them to the ClientGUI via a callback interface.
 */
public class ChatClient {

    // ── Callback interface ────────────────────────────────────────────────────

    public interface ServerListener {
        void onAuthSuccess();
        void onAuthFailure(String reason);
        void onMessageReceived(String line);
        void onDisconnected();
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    private static final String PROPERTIES_FILE = "client.properties";

    private final String host;
    private final int    port;

    // ── State ─────────────────────────────────────────────────────────────────

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private ServerListener listener;

    /** True after LOGIN/REGISTER is acknowledged with OK. */
    private volatile boolean authenticated = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Properties props = loadProperties();
        String host = props.getProperty("server.host", "127.0.0.1");
        int    port = Integer.parseInt(props.getProperty("server.port", "5000"));

        ChatClient client = new ChatClient(host, port);

        javax.swing.SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI(client);
            gui.setVisible(true);
        });
    }

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Opens the socket connection and starts the background reader thread.
     *
     * @param listener receives server events
     * @throws IOException if the connection cannot be established
     */
    public void connect(ServerListener listener) throws IOException {
        this.listener = listener;
        socket = new Socket(host, port);

        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"),
                true /* auto-flush */);

        // Read incoming lines on a daemon thread
        Thread readThread = new Thread(this::readLoop, "server-reader");
        readThread.setDaemon(true);
        readThread.start();
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public void sendLogin(String username, String password) {
        sendLine("LOGIN|" + username + "|" + password);
    }

    public void sendRegister(String username, String password) {
        sendLine("REGISTER|" + username + "|" + password);
    }

    public void sendChat(String text) {
        if (authenticated) {
            sendLine("CHAT|" + text);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void sendLine(String line) {
        if (writer != null && !socket.isClosed()) {
            writer.println(line);
        }
    }

    /**
     * Continuously reads lines from the server until the connection closes.
     *
     * Server → client protocol:
     *   OK          — authentication success
     *   ERROR|msg   — authentication failure or server error
     *   MSG|line    — chat message (history or live)
     */
    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("OK")) {
                    authenticated = true;
                    listener.onAuthSuccess();
                } else if (line.startsWith("ERROR|")) {
                    listener.onAuthFailure(line.substring(6));
                } else if (line.startsWith("MSG|")) {
                    listener.onMessageReceived(line.substring(4));
                }
            }
        } catch (IOException e) {
            // Connection closed — expected when server shuts down or we disconnect
        } finally {
            authenticated = false;
            listener.onDisconnected();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = ChatClient.class
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

    public String getHost() { return host; }
    public int    getPort() { return port; }
}