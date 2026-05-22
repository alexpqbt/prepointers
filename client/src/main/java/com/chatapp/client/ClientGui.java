package com.chatapp.client;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClientGui extends JFrame {

    private final Config config = new Config();
    private ServerConnection connection;
    private String username;

    // Auth panel
    private final JPanel authPanel = new JPanel(new GridBagLayout());
    private final JTextField userField = new JTextField(18);
    private final JPasswordField passField = new JPasswordField(18);
    private final JButton loginBtn = new JButton("Login");
    private final JButton registerBtn = new JButton("Register");
    private final JLabel authStatus = new JLabel(" ");

    // Chat panel
    private final JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
    private final JTextPane chatPane = new JTextPane();
    private final JTextField inputField = new JTextField();
    private final JButton sendBtn = new JButton("Send");
    private final JLabel statusLabel = new JLabel("● Offline");
    private final JButton reconnectBtn = new JButton("Reconnect");

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final SimpleAttributeSet STYLE_DEFAULT = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_SYSTEM_JOIN = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_SYSTEM_LEAVE = new SimpleAttributeSet();
    private static final SimpleAttributeSet STYLE_ERROR = new SimpleAttributeSet();

    static {
        StyleConstants.setForeground(STYLE_DEFAULT, Color.BLACK);
        StyleConstants.setForeground(STYLE_SYSTEM_JOIN, new Color(0, 140, 0));
        StyleConstants.setForeground(STYLE_SYSTEM_LEAVE, new Color(180, 0, 0));
        StyleConstants.setForeground(STYLE_ERROR, Color.RED);
        StyleConstants.setBold(STYLE_SYSTEM_JOIN, true);
        StyleConstants.setBold(STYLE_SYSTEM_LEAVE, true);
    }

    public ClientGui() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);

        buildAuthPanel();
        buildChatPanel();

        root.add(authPanel, "auth");
        root.add(chatPanel, "chat");
        add(root);

        cards.show(root, "auth");
        connectToServer();
    }

    // ─── Auth Panel ───────────────────────────────────────────────────────────

    private void buildAuthPanel() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Chat Application", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        authPanel.add(title, c);

        c.gridwidth = 1;
        c.gridy = 1; c.gridx = 0; authPanel.add(new JLabel("Username:"), c);
        c.gridx = 1; authPanel.add(userField, c);

        c.gridy = 2; c.gridx = 0; authPanel.add(new JLabel("Password:"), c);
        c.gridx = 1; authPanel.add(passField, c);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.add(loginBtn);
        btnRow.add(registerBtn);
        c.gridy = 3; c.gridx = 0; c.gridwidth = 2;
        authPanel.add(btnRow, c);

        authStatus.setHorizontalAlignment(SwingConstants.CENTER);
        authStatus.setForeground(Color.RED);
        c.gridy = 4;
        authPanel.add(authStatus, c);

        loginBtn.addActionListener(e -> sendAuth("LOGIN"));
        registerBtn.addActionListener(e -> sendAuth("REGISTER"));
        passField.addActionListener(e -> sendAuth("LOGIN"));
    }

    private void sendAuth(String cmd) {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword()).trim();
        if (user.isEmpty() || pass.isEmpty()) {
            authStatus.setText("Username and password are required.");
            return;
        }
        if (!connection.isConnected()) {
            authStatus.setText("Not connected to server.");
            return;
        }
        authStatus.setText("Waiting...");
        username = user;
        connection.send(cmd + "|" + user + "|" + pass);
    }

    // ─── Chat Panel ───────────────────────────────────────────────────────────

    private void buildChatPanel() {
        chatPane.setEditable(false);
        chatPane.setContentType("text/plain");
        JScrollPane scroll = new JScrollPane(chatPane);

        JPanel inputRow = new JPanel(new BorderLayout(5, 0));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        JPanel bottomBar = new JPanel(new BorderLayout(5, 0));
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusRow.add(statusLabel);
        statusRow.add(reconnectBtn);
        reconnectBtn.setVisible(false);
        bottomBar.add(statusRow, BorderLayout.WEST);
        bottomBar.add(inputRow, BorderLayout.CENTER);

        chatPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        chatPanel.add(scroll, BorderLayout.CENTER);
        chatPanel.add(bottomBar, BorderLayout.SOUTH);

        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        reconnectBtn.addActionListener(e -> reconnect());
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (!connection.isConnected()) {
            appendStyled("[Not connected]\n", STYLE_ERROR);
            return;
        }
        connection.send("CHAT|" + text);
        inputField.setText("");
    }

    // ─── Connection & message handling ───────────────────────────────────────

    private void connectToServer() {
        connection = new ServerConnection(config, this::handleMessage, this::handleStatus);
        new Thread(() -> {
            boolean ok = connection.connect();
            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    setOnline(true);
                } else {
                    setOnline(false);
                }
            });
        }, "connect-thread").start();
    }

    private void reconnect() {
        connection.close();
        reconnectBtn.setVisible(false);
        connectToServer();
    }

    private void handleMessage(String line) {
        SwingUtilities.invokeLater(() -> processLine(line));
    }

    private void processLine(String line) {
        if (line.startsWith("OK")) {
            // Auth success
            cards.show(root, "chat");
            setTitle("Chat – " + username);
            setOnline(true);
        } else if (line.startsWith("ERROR|")) {
            String msg = line.substring(6);
            // Could be auth error or in-chat error
            if (isChatVisible()) {
                appendStyled("[ERROR] " + msg + "\n", STYLE_ERROR);
            } else {
                authStatus.setText(msg);
            }
        } else if (line.startsWith("MSG|")) {
            String[] p = line.split("\\|", 3);
            if (p.length == 3) {
                String ts = LocalTime.now().format(TIME_FMT);
                appendStyled("[" + ts + "] " + p[1] + ": " + p[2] + "\n", STYLE_DEFAULT);
            }
        } else if (line.startsWith("SYSTEM|")) {
            String msg = line.substring(7);
            String ts = LocalTime.now().format(TIME_FMT);
            String full = "[" + ts + "] [SYSTEM] " + msg + "\n";
            if (msg.contains("joined")) {
                appendStyled(full, STYLE_SYSTEM_JOIN);
            } else {
                appendStyled(full, STYLE_SYSTEM_LEAVE);
            }
        }
    }

    private void handleStatus(String msg) {
        SwingUtilities.invokeLater(() -> {
            boolean online = msg.startsWith("Connected");
            setOnline(online);
            if (!online) {
                reconnectBtn.setVisible(true);
            }
        });
    }

    private void setOnline(boolean online) {
        if (online) {
            statusLabel.setText("● Online");
            statusLabel.setForeground(new Color(0, 140, 0));
            reconnectBtn.setVisible(false);
        } else {
            statusLabel.setText("● Offline");
            statusLabel.setForeground(Color.RED);
        }
    }

    private void appendStyled(String text, AttributeSet style) {
        Document doc = chatPane.getDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {}
        chatPane.setCaretPosition(doc.getLength());
    }

    private boolean isChatVisible() {
        return chatPanel.isShowing();
    }
}