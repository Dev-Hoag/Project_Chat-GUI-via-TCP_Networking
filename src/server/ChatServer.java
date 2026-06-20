package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import server.observer.ServerBroadcaster;
import server.router.MessageRouter;
import server.service.auth.AuthService;
import server.service.client.ClientManager;
import server.service.client.IClientManager;
import server.service.database.DatabaseManager;
import server.service.database.IDatabase;
import server.service.avatar.AvatarService;
import server.service.file.FileService;
import server.service.file.IFileService;
import server.service.profile.ProfileService;
import server.service.user.IUserRepository;

public class ChatServer {
    // Port mặc định nếu không truyền tham số khi chạy server.
    private static final int DEFAULT_PORT = 5001;

    // Điểm vào của ứng dụng server. Khởi tạo manager, DB, file service và router,
    // sau đó chấp nhận kết nối TCP mới liên tục.
    public static void main(String[] args) {
        int port = parsePort(args);
        IClientManager clientManager = new ClientManager();
        IDatabase databaseManager = new DatabaseManager();
        IUserRepository userRepository = (IUserRepository) databaseManager;
        IFileService fileService = new FileService();
        ServerBroadcaster broadcaster = new ServerBroadcaster();
        AvatarService avatarService = new AvatarService();
        AuthService authService = new AuthService(userRepository);
        MessageRouter router = new MessageRouter(clientManager, databaseManager, broadcaster);
        ProfileService profileService = new ProfileService(userRepository, avatarService, router);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCPChatGUI server started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                try {
                    ClientHandler handler = new ClientHandler(socket, clientManager, router, fileService, broadcaster, authService, profileService);
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

    // Lấy port từ tham số dòng lệnh, hoặc dùng port mặc định nếu không có tham số.
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
