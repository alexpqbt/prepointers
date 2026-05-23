package com.chatapp.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * ServerGUI — Swing window for the chat server.
 *
 * Displays:
 *  - Live chat log (top panel)
 *  - Connected-user count (status bar)
 *  - Console / diagnostic log (bottom panel)
 */
public class ServerGUI extends JFrame {

    private final JTextArea chatLogArea;
    private final JTextArea consoleArea;
    private final JLabel    clientCountLabel;

    public ServerGUI(String host, int port) {
        super("Chat Server — " + host + ":" + port);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);

        // ── Status bar ────────────────────────────────────────────────────────
        clientCountLabel = new JLabel(" Connected clients: 0");
        clientCountLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        clientCountLabel.setBorder(new EmptyBorder(4, 6, 4, 6));
        clientCountLabel.setOpaque(true);
        clientCountLabel.setBackground(new Color(230, 245, 255));

        // ── Chat log ──────────────────────────────────────────────────────────
        chatLogArea = buildTextArea();
        JScrollPane chatScroll = new JScrollPane(chatLogArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Live Chat Log"));

        // ── Console log ───────────────────────────────────────────────────────
        consoleArea = buildTextArea();
        consoleArea.setForeground(new Color(0, 100, 0));
        consoleArea.setBackground(new Color(240, 255, 240));
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Server Console"));

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, chatScroll, consoleScroll);
        split.setDividerLocation(350);
        split.setResizeWeight(0.65);

        // ── Layout ────────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        root.add(clientCountLabel, BorderLayout.NORTH);
        root.add(split,            BorderLayout.CENTER);

        setContentPane(root);
    }

    // ── Public update methods (call from any thread) ───────────────────────────

    public void appendChatLog(String line) {
        SwingUtilities.invokeLater(() -> {
            chatLogArea.append(line + "\n");
            chatLogArea.setCaretPosition(chatLogArea.getDocument().getLength());
        });
    }

    public void appendConsoleLog(String line) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(line + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    public void setClientCount(int count) {
        SwingUtilities.invokeLater(() ->
                clientCountLabel.setText(" Connected clients: " + count));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private JTextArea buildTextArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }
}