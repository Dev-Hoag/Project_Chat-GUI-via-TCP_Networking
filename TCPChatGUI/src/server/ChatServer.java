package server;

import common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class ChatServer {
    private final int port;
    private final ClientManager clientManager;
    private final MessageRouter messageRouter;

    public ChatServer(int port) {
        this.port = port;
        this.clientManager = new ClientManager();
        DatabaseManager databaseManager = new DatabaseManager();
        FileService fileService = new FileService(Path.of("server_files"));
        this.messageRouter = new MessageRouter(clientManager, databaseManager, fileService);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] TCPChatGUI server started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                try {
                    ClientHandler handler = new ClientHandler(socket, clientManager, messageRouter);
                    new Thread(handler, "client-handler-" + socket.getPort()).start();
                } catch (IOException ex) {
                    socket.close();
                    System.err.println("[Server] Cannot create client handler: " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot start server on port " + port, ex);
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        new ChatServer(port).start();
    }
}
