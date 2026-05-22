package com.chatapp.client;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGui gui = new ClientGui();
            gui.setVisible(true);
        });
    }
}