package com.chatapp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    public static final int DEFAULT_PORT = 5000;
    public static final String DELIMITER = "|";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int port;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public Server() {
        this(DEFAULT_PORT);
    }

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] TCP chat server started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(socket, this);
                new Thread(handler, "client-handler-" + socket.getPort()).start();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot start server on port " + port, ex);
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Server(port).start();
    }

    boolean addClient(String username, ClientHandler handler) {
        return clients.putIfAbsent(username, handler) == null;
    }

    void removeClient(String username, ClientHandler handler) {
        if (username != null) {
            clients.remove(username, handler);
        }
    }

    ClientHandler getClient(String username) {
        return clients.get(username);
    }

    List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>(clients.keySet());
        Collections.sort(users);
        return users;
    }

    void broadcast(String line) {
        for (ClientHandler handler : clients.values()) {
            handler.sendLine(line);
        }
    }

    void broadcastUserList() {
        broadcast("USER_LIST" + DELIMITER + String.join(",", getOnlineUsers()));
    }

    void broadcastJoin(String username) {
        broadcast("JOIN" + DELIMITER + username + DELIMITER + now());
    }

    void broadcastLeave(String username) {
        broadcast("LEAVE" + DELIMITER + username + DELIMITER + now());
    }

    static String now() {
        return TIME_FORMAT.format(LocalDateTime.now());
    }
}
