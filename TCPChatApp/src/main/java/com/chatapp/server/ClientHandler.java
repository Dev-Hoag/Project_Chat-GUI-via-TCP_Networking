package com.chatapp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final Object sendLock = new Object();

    private volatile String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    @Override
    public void run() {
        try {
            if (!handleLogin()) {
                return;
            }
            String line;
            while (running && (line = reader.readLine()) != null) {
                route(line);
            }
        } catch (IOException ex) {
            System.out.println("[Server] Client disconnected: " + displayName());
        } finally {
            cleanup();
        }
    }

    void sendLine(String line) {
        synchronized (sendLock) {
            if (running) {
                writer.println(line);
            }
        }
    }

    private boolean handleLogin() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return false;
        }
        String[] parts = line.split("\\" + Server.DELIMITER, 2);
        if (parts.length < 2 || !"LOGIN".equals(parts[0])) {
            sendError("First command must be LOGIN|username");
            return false;
        }

        String requestedUsername = parts[1].trim();
        if (!isValidUsername(requestedUsername)) {
            sendError("Username must be 2-30 characters");
            return false;
        }
        if (!server.addClient(requestedUsername, this)) {
            sendError("Username already exists");
            return false;
        }

        username = requestedUsername;
        sendLine("LOGIN_SUCCESS");
        server.broadcastUserList();
        server.broadcastJoin(username);
        System.out.println("[Server] " + username + " logged in.");
        return true;
    }

    private void route(String line) {
        String[] parts = line.split("\\" + Server.DELIMITER, -1);
        String command = parts.length == 0 ? "" : parts[0];

        switch (command) {
            case "PUBLIC_MSG" -> handlePublicMessage(parts);
            case "PRIVATE_MSG" -> handlePrivateMessage(parts);
            case "USER_LIST" -> server.broadcastUserList();
            case "QUIT" -> running = false;
            default -> sendError("Unknown command: " + command);
        }
    }

    private void handlePublicMessage(String[] parts) {
        if (parts.length < 2) {
            sendError("Message is empty");
            return;
        }
        String message = parts[1].trim();
        if (message.isEmpty()) {
            return;
        }
        server.broadcast(String.join(Server.DELIMITER,
                "PUBLIC_MSG",
                username,
                message,
                Server.now()));
    }

    private void handlePrivateMessage(String[] parts) {
        if (parts.length < 3) {
            sendError("Private message is invalid");
            return;
        }

        String receiver = parts[1].trim();
        String message = parts[2].trim();
        if (receiver.isEmpty() || message.isEmpty()) {
            return;
        }

        ClientHandler receiverHandler = server.getClient(receiver);
        if (receiverHandler == null) {
            sendError("User is offline");
            return;
        }

        String payload = String.join(Server.DELIMITER,
                "PRIVATE_MSG",
                username,
                message,
                Server.now());
        receiverHandler.sendLine(payload);
        sendLine(String.join(Server.DELIMITER,
                "PRIVATE_MSG_SENT",
                receiver,
                message,
                Server.now()));
    }

    private boolean isValidUsername(String value) {
        return value != null && value.length() >= 2 && value.length() <= 30 && !value.contains(",");
    }

    private void sendError(String message) {
        sendLine("ERROR" + Server.DELIMITER + message);
    }

    private String displayName() {
        return username == null ? socket.getRemoteSocketAddress().toString() : username;
    }

    private void cleanup() {
        running = false;
        if (username != null) {
            server.removeClient(username, this);
            server.broadcastLeave(username);
            server.broadcastUserList();
            System.out.println("[Server] " + username + " logged out.");
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
