package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private static final int DEFAULT_PORT = 5001;

    public static void main(String[] args) {
        int port = parsePort(args);
        ClientManager clientManager = new ClientManager();
        DatabaseManager databaseManager = new DatabaseManager();
        FileService fileService = new FileService();
        MessageRouter router = new MessageRouter(clientManager, databaseManager);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCPChatGUI server started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                try {
                    ClientHandler handler = new ClientHandler(socket, clientManager, router, fileService);
                    Thread thread = new Thread(handler, "client-" + socket.getRemoteSocketAddress());
                    thread.start();
                } catch (IOException e) {
                    System.out.println("[SERVER] Cannot create client handler: " + e.getMessage());
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("[SERVER] Cannot start server: " + e.getMessage());
        }
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
