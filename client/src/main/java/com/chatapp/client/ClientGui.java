package com.chatapp.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * ClientGUI — single-window Swing UI for the chat client.
 *
 * The window has two "cards" managed by a CardLayout:
 *   1. AUTH_CARD  — login / register form
 *   2. CHAT_CARD  — public chat display + input field
 *
 * All network callbacks arrive on background threads and are safely
 * dispatched to the Event Dispatch Thread via SwingUtilities.invokeLater().
 */
public class ClientGUI extends JFrame implements ChatClient.ServerListener {

    // ── Card names ────────────────────────────────────────────────────────────

    private static final String AUTH_CARD = "AUTH";
    private static final String CHAT_CARD = "CHAT";

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ChatClient client;

    // ── Shared widgets ────────────────────────────────────────────────────────

    private final CardLayout cardLayout    = new CardLayout();
    private final JPanel     cardPanel     = new JPanel(cardLayout);
    private final JLabel     statusLabel   = new JLabel("Disconnected");

    // ── Auth card ─────────────────────────────────────────────────────────────

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JLabel         authErrorLabel;

    // ── Chat card ─────────────────────────────────────────────────────────────

    private JTextArea  chatArea;
    private JTextField inputField;
    private JButton    sendButton;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ClientGUI(ChatClient client) {
        super("Chat Client");
        this.client = client;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 520);
        setLocationRelativeTo(null);
        setResizable(true);

        buildUI();
        connectToServer();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        cardPanel.add(buildAuthCard(), AUTH_CARD);
        cardPanel.add(buildChatCard(), CHAT_CARD);

        // Status bar at the bottom
        statusLabel.setBorder(new EmptyBorder(3, 8, 3, 8));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(240, 240, 240));

        JPanel root = new JPanel(new BorderLayout());
        root.add(cardPanel,   BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    // ── Auth card ─────────────────────────────────────────────────────────────

    private JPanel buildAuthCard() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(new Color(245, 248, 252));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 225)),
                new EmptyBorder(24, 32, 24, 32)));
        form.setMaximumSize(new Dimension(340, 360));

        JLabel title = new JLabel("Chat Application");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Login or Register to continue");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        usernameField = new JTextField(18);
        passwordField = new JPasswordField(18);
        styleTextField(usernameField);
        styleTextField(passwordField);

        JButton loginBtn    = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        styleButton(loginBtn,    new Color(52, 120, 220));
        styleButton(registerBtn, new Color(80, 160, 80));

        loginBtn.addActionListener(e -> attemptLogin());
        registerBtn.addActionListener(e -> attemptRegister());

        // Allow Enter key in password field to trigger login
        passwordField.addActionListener(e -> attemptLogin());

        authErrorLabel = new JLabel(" ");
        authErrorLabel.setForeground(new Color(180, 30, 30));
        authErrorLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        authErrorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setBackground(Color.WHITE);
        btnRow.add(loginBtn);
        btnRow.add(registerBtn);

        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(18));
        form.add(labeledField("Username:", usernameField));
        form.add(Box.createVerticalStrut(10));
        form.add(labeledField("Password:", passwordField));
        form.add(Box.createVerticalStrut(16));
        form.add(btnRow);
        form.add(Box.createVerticalStrut(10));
        form.add(authErrorLabel);

        outer.add(form);
        return outer;
    }

    private JPanel labeledField(String labelText, JComponent field) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(lbl);
        p.add(Box.createVerticalStrut(3));
        p.add(field);
        return p;
    }

    // ── Chat card ─────────────────────────────────────────────────────────────

    private JPanel buildChatCard() {
        // Chat display
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Public Chat"));

        // Input row
        inputField = new JTextField();
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendButton.setBackground(new Color(52, 120, 220));
        sendButton.setForeground(Color.WHITE);
        sendButton.setOpaque(true);
        sendButton.setBorderPainted(false);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBorder(new EmptyBorder(6, 6, 6, 6));
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton,  BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(6, 6, 0, 6));
        panel.add(chatScroll, BorderLayout.CENTER);
        panel.add(inputRow,   BorderLayout.SOUTH);
        return panel;
    }

    // ── Networking ────────────────────────────────────────────────────────────

    private void connectToServer() {
        setStatus("Connecting to " + client.getHost() + ":" + client.getPort() + " …", Color.ORANGE);
        // Connect on a worker thread; UI will be updated via callbacks
        Thread t = new Thread(() -> {
            try {
                client.connect(this);
                setStatus("Connected — please login or register.", new Color(0, 130, 0));
            } catch (IOException e) {
                setStatus("Cannot connect to server: " + e.getMessage(), Color.RED);
            }
        }, "connect-thread");
        t.setDaemon(true);
        t.start();
    }

    private void attemptLogin() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword()).trim();
        if (user.isEmpty() || pass.isEmpty()) {
            showAuthError("Username and password are required.");
            return;
        }
        authErrorLabel.setText(" ");
        client.sendLogin(user, pass);
    }

    private void attemptRegister() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword()).trim();
        if (user.isEmpty() || pass.isEmpty()) {
            showAuthError("Username and password are required.");
            return;
        }
        authErrorLabel.setText(" ");
        client.sendRegister(user, pass);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        client.sendChat(text);
        inputField.setText("");
    }

    // ── ServerListener callbacks (called from background thread) ─────────────

    @Override
    public void onAuthSuccess() {
        SwingUtilities.invokeLater(() -> {
            String user = usernameField.getText().trim();
            setTitle("Chat Client — " + user);
            setStatus("Online as " + user, new Color(0, 130, 0));
            cardLayout.show(cardPanel, CHAT_CARD);
            inputField.requestFocusInWindow();
        });
    }

    @Override
    public void onAuthFailure(String reason) {
        SwingUtilities.invokeLater(() -> showAuthError(reason));
    }

    @Override
    public void onMessageReceived(String line) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(line + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            setStatus("Disconnected from server.", Color.RED);
            sendButton.setEnabled(false);
            inputField.setEnabled(false);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showAuthError(String msg) {
        authErrorLabel.setText(msg);
    }

    private void setStatus(String text, Color fg) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("  " + text);
            statusLabel.setForeground(fg);
        });
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(110, 34));
    }
}