package com.chatapp.server;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServerGui extends JFrame {

    private final Config config = new Config();
    private final UserStore userStore = new UserStore();
    private ChatServer chatServer;

    private final JTextArea chatLog = new JTextArea();
    private final JTextArea consoleLog = new JTextArea();
    private final JLabel clientCountLabel = new JLabel("Connected: 0");
    private final JButton startBtn = new JButton("Start");
    private final JButton stopBtn = new JButton("Stop");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ServerGui() {
        setTitle("Chat Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 550);
        setLocationRelativeTo(null);
        buildUi();
        wireActions();
        autoStart();
    }

    private void buildUi() {
        setLayout(new BorderLayout(5, 5));

        // Top bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBar.add(new JLabel("Server Control:"));
        topBar.add(startBtn);
        topBar.add(stopBtn);
        topBar.add(clientCountLabel);
        stopBtn.setEnabled(false);
        add(topBar, BorderLayout.NORTH);

        // Chat log
        chatLog.setEditable(false);
        chatLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane chatScroll = new JScrollPane(chatLog);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Chat Log"));

        // Console log
        consoleLog.setEditable(false);
        consoleLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleLog.setBackground(new Color(30, 30, 30));
        consoleLog.setForeground(new Color(180, 255, 180));
        JScrollPane consoleScroll = new JScrollPane(consoleLog);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Console"));
        consoleScroll.setPreferredSize(new Dimension(0, 160));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatScroll, consoleScroll);
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);
    }

    private void wireActions() {
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
    }

    private void autoStart() {
        SwingUtilities.invokeLater(this::startServer);
    }

    private void startServer() {
        chatServer = new ChatServer(config, userStore, this::appendConsole, this::updateClientCount, this::displayBroadcast);
        try {
            chatServer.start();
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
        } catch (IOException e) {
            appendConsole("ERROR: Could not start server - " + e.getMessage());
        }
    }

    private void stopServer() {
        if (chatServer != null) chatServer.stop();
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void displayBroadcast(String raw) {
        if (raw.startsWith("MSG|")) {
            String[] p = raw.split("\\|", 3);
            if (p.length == 3) appendChatLog(p[1] + ": " + p[2]);
        } else if (raw.startsWith("SYSTEM|")) {
            appendChatLog("[SYSTEM] " + raw.substring(7));
        }
    }

    public void appendChatLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatLog.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
            scrollToBottom(chatLog);
        });
    }

    public void appendConsole(String msg) {
        SwingUtilities.invokeLater(() -> {
            consoleLog.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
            scrollToBottom(consoleLog);
        });
    }

    private void updateClientCount(int count) {
        SwingUtilities.invokeLater(() -> clientCountLabel.setText("Connected: " + count));
    }

    private void scrollToBottom(JTextArea area) {
        area.setCaretPosition(area.getDocument().getLength());
    }
}