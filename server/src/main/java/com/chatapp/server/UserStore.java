package com.chatapp.server;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class UserStore {

    private static final String CSV_FILE =
            System.getProperty("user.home") + java.io.File.separator + "chatapp_users.csv";
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,19}$");
    private static final Pattern CONSECUTIVE_SYMBOLS =
            Pattern.compile("[_\\-]{2,}|[_\\-][_\\-]");

    private final Path csvPath;
    private final Map<String, String> users = new LinkedHashMap<>();

    public UserStore() {
        csvPath = Paths.get(CSV_FILE);
        loadOrCreate();
    }

    private synchronized void loadOrCreate() {
        if (!Files.exists(csvPath)) {
            try {
                Files.writeString(csvPath, "username,password\n");
            } catch (IOException e) {
                System.err.println("Failed to create users.csv: " + e.getMessage());
            }
            return;
        }
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    users.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read users.csv: " + e.getMessage());
        }
    }

    private synchronized void persist() {
        try (BufferedWriter bw = Files.newBufferedWriter(csvPath)) {
            bw.write("username,password\n");
            for (Map.Entry<String, String> e : users.entrySet()) {
                bw.write(e.getKey() + "," + e.getValue() + "\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to save users.csv: " + e.getMessage());
        }
    }

    public String validateUsername(String username) {
        if (username == null || username.isEmpty())
            return "Username cannot be empty.";
        if (username.length() > 20)
            return "Username must be 20 characters or fewer.";
        if (!USERNAME_PATTERN.matcher(username).matches())
            return "Username must start with a letter and contain only letters, digits, _ or -.";
        if (CONSECUTIVE_SYMBOLS.matcher(username).find())
            return "Username cannot contain consecutive special characters.";
        return null;
    }

    public synchronized String register(String username, String password) {
        String usernameError = validateUsername(username);
        if (usernameError != null) return "ERROR|" + usernameError;
        if (password == null || password.length() < 4)
            return "ERROR|Password must be at least 4 characters.";
        if (users.containsKey(username))
            return "ERROR|Username already exists.";
        users.put(username, password);
        persist();
        return "OK";
    }

    public synchronized String login(String username, String password) {
        if (!users.containsKey(username))
            return "ERROR|Username not found.";
        if (!users.get(username).equals(password))
            return "ERROR|Incorrect password.";
        return "OK";
    }
}