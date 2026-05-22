package com.chatapp.server;

import javax.swing.*;

public class ServerMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGui gui = new ServerGui();
            gui.setVisible(true);
        });
    }
}